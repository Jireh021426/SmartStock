package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.SalesService;
import com.smartstock.service.PaymentRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
 properties={"spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("test")
class PaymentIdempotencyTest {
 @Autowired DataSource dataSource;
 @Autowired StockLocationRepository locations;
 @Autowired EmployeeRepository employees;
 @Autowired CustomerRepository customers;
 @Autowired CreditPaymentRepository payments;
 @Autowired SalesService sales;
 @Autowired PaymentRecoveryService paymentRecovery;
 @SpyBean SalesService salesSpy;
 @MockBean LegacyInventoryMigration legacyInventoryMigration;
 @MockBean(name="temporaryAdminService") UserDetailsService temporaryAdminService;
 String username;
 Long customerId;

 @BeforeEach void setup() throws Exception {
  try(Connection c=dataSource.getConnection()) {
   assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:smartstock_test"),"Unsafe database");
  }
  String suffix=UUID.randomUUID().toString().replace("-","");
  StockLocation stall=locations.save(new StockLocation("I"+suffix.substring(0,15),"Idempotency Stall","STALL"));
  Employee cashier=employees.save(new Employee("Idempotency Cashier","idem_"+suffix,"test-hash",EmployeeRole.CASHIER,stall));
  Customer customer=new Customer("Idempotency Customer","09"+suffix.substring(0,15));
  customer.charge(new BigDecimal("2000.00"));
  customer=customers.save(customer);
  username=cashier.getUsername(); customerId=customer.getId();
 }
 PaymentForm form(String key, String amount) {
  PaymentForm f=new PaymentForm();f.setRequestId(key);f.setCustomerId(customerId);
  f.setAmount(new BigDecimal(amount));f.setMethod(PaymentMethod.CASH);return f;
 }
 void assertBalance(String amount) {assertEquals(0,customers.findById(customerId).orElseThrow().getBalance().compareTo(new BigDecimal(amount)));}
 @Test void retryDoesNotChargeTwice() {
  String key=UUID.randomUUID().toString();
  sales.recordPayment(username,form(key,"500.00"));
  sales.recordPayment(username,form(key,"500.00"));
  assertBalance("1500.00");
  assertEquals(1,payments.findAll().stream().filter(p->key.equals(p.getRequestId())).count());
 }
 @Test void changedPayloadWithSameKeyRejected() {
  String key=UUID.randomUUID().toString();
  sales.recordPayment(username,form(key,"500.00"));
  assertThrows(IllegalArgumentException.class,()->sales.recordPayment(username,form(key,"600.00")));
  assertBalance("1500.00");
 }
 @Test void distinctKeysPermitSameAmount() {
  sales.recordPayment(username,form(UUID.randomUUID().toString(),"500.00"));
  sales.recordPayment(username,form(UUID.randomUUID().toString(),"500.00"));
  assertBalance("1000.00");
 }
 @Test void simultaneousRetryChargesOnce() throws Exception {
  String key=UUID.randomUUID().toString();
  ExecutorService pool=Executors.newFixedThreadPool(2);
  CountDownLatch start=new CountDownLatch(1);
  try {
   Callable<Void> action=()->{start.await();sales.recordPayment(username,form(key,"500.00"));return null;};
   Future<Void> a=pool.submit(action),b=pool.submit(action);
   start.countDown();a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);
  } finally {pool.shutdownNow();}
  assertBalance("1500.00");
  assertEquals(1,payments.findAll().stream().filter(p->key.equals(p.getRequestId())).count());
 }

  @Test
  void differentCashiersSameRequestId() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "");

    StockLocation secondStall = locations.save(
        new StockLocation(
            "D" + suffix.substring(0, 15),
            "Second Cashier Stall",
            "STALL"
        )
    );

    Employee secondCashier = employees.save(
        new Employee(
            "Second Cashier",
            "idem_second_" + suffix,
            "test-hash",
            EmployeeRole.CASHIER,
            secondStall
        )
    );

    String secondUsername = secondCashier.getUsername();
    String key = UUID.randomUUID().toString();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try {
      Callable<Throwable> first = () -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Start timeout");
        }

        try {
          paymentRecovery.recordPayment(username, form(key, "500.00"));
          return null;
        } catch (Exception ex) {
          return ex;
        }
      };

      Callable<Throwable> second = () -> {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Start timeout");
        }

        try {
          paymentRecovery.recordPayment(secondUsername, form(key, "500.00"));
          return null;
        } catch (Exception ex) {
          return ex;
        }
      };

      Future<Throwable> a = pool.submit(first);
      Future<Throwable> b = pool.submit(second);

      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();

      Throwable errorA = a.get(20, TimeUnit.SECONDS);
      Throwable errorB = b.get(20, TimeUnit.SECONDS);

      System.out.println("Cashier A result: " + errorA);
      System.out.println("Cashier B result: " + errorB);

      assertTrue(
          errorA == null || errorA instanceof IllegalArgumentException,
          "Cashier A received an unexpected exception: " + errorA
      );

      assertTrue(
          errorB == null || errorB instanceof IllegalArgumentException,
          "Cashier B received an unexpected exception: " + errorB
      );

      assertTrue(
          (errorA == null) != (errorB == null),
          "Exactly one cashier must succeed"
      );

      Throwable rejected = errorA != null ? errorA : errorB;

      assertInstanceOf(IllegalArgumentException.class, rejected);

      assertEquals(
          "Payment request ID already used for different payment details",
          rejected.getMessage()
      );

    } finally {
      start.countDown();
      pool.shutdownNow();
    }

    assertBalance("1500.00");

    assertEquals(
        1,
        payments.findAll().stream()
            .filter(p -> key.equals(p.getRequestId()))
            .count()
    );
  }


 @Test
 void matchingPaymentAfterDatabaseConflictShowsDuplicateWarning() {
  String key = UUID.randomUUID().toString();
  PaymentForm original = form(key, "500.00");

  sales.recordPayment(username, original);

  doThrow(new DataIntegrityViolationException("Simulated conflict"))
      .when(salesSpy)
      .recordPayment(eq(username), any(PaymentForm.class));

  IllegalArgumentException error = assertThrows(
      IllegalArgumentException.class,
      () -> paymentRecovery.recordPayment(
          username, form(key, "500.00"))
  );

  assertEquals(
      "Payment request ID already used. Check payment history before trying again.",
      error.getMessage()
  );

  assertBalance("1500.00");

  assertEquals(
      1,
      payments.findAll().stream()
          .filter(p -> key.equals(p.getRequestId()))
          .count()
  );
 }


 @Test
 void unrelatedDatabaseConflictPreservesOriginalException() {
  String key = UUID.randomUUID().toString();

  DataIntegrityViolationException original =
      new DataIntegrityViolationException("Simulated unrelated database error");

  doThrow(original)
      .when(salesSpy)
      .recordPayment(eq(username), any(PaymentForm.class));

  DataIntegrityViolationException actual = assertThrows(
      DataIntegrityViolationException.class,
      () -> paymentRecovery.recordPayment(
          username, form(key, "500.00"))
  );

  assertSame(original, actual);
  assertBalance("2000.00");

  assertTrue(payments.findByRequestId(key).isEmpty());
 }

}
