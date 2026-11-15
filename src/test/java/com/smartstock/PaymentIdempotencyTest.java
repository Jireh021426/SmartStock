package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.SalesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
}
