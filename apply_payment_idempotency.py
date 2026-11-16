from pathlib import Path
import re

root = Path.cwd()
paths = {
 'entity': root/'src/main/java/com/smartstock/model/CreditPayment.java',
 'form': root/'src/main/java/com/smartstock/model/PaymentForm.java',
 'repo': root/'src/main/java/com/smartstock/repository/CreditPaymentRepository.java',
 'service': root/'src/main/java/com/smartstock/service/SalesService.java',
 'controller': root/'src/main/java/com/smartstock/controller/SalesController.java',
 'html': root/'src/main/resources/templates/payment-form.html',
 'oldtest': root/'src/test/java/com/smartstock/PaymentRemittanceConcurrencyTest.java',
 'newtest': root/'src/test/java/com/smartstock/PaymentIdempotencyTest.java',
}
for key,p in paths.items():
 if key != 'newtest' and not p.is_file(): raise SystemExit(f'Missing file: {p}')
 if key == 'newtest' and p.exists(): raise SystemExit(f'Will not overwrite existing test: {p}')
original = {k:p.read_text(encoding='utf-8') for k,p in paths.items() if k!='newtest'}
modified = dict(original)
def replace_once(key, old, new):
 s=modified[key]
 if s.count(old)!=1: raise SystemExit(f'Unexpected source structure in {paths[key]}: expected one occurrence of {old[:65]!r}, found {s.count(old)}. No files changed.')
 modified[key]=s.replace(old,new,1)

replace_once('entity','@Entity\npublic class CreditPayment {','@Entity\n@Table(uniqueConstraints = @UniqueConstraint(name = "uk_credit_payment_request_id", columnNames = "request_id"))\npublic class CreditPayment {')
replace_once('entity',' @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;', ' @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;\n @Column(name="request_id", length=36, unique=true) private String requestId;')
replace_once('entity',' public Long getId(){return id;}', ' public void assignRequestId(String value){if(value==null || !value.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Valid payment request ID required");requestId=value;}\n public String getRequestId(){return requestId;}\n public Long getId(){return id;}')
replace_once('form',' public Long getCustomerId(){return customerId;}', ' @NotBlank private String requestId;\n public String getRequestId(){return requestId;} public void setRequestId(String x){requestId=x;}\n public Long getCustomerId(){return customerId;}')
replace_once('repo','import java.util.List;', 'import java.util.List;\nimport java.util.Optional;')
replace_once('repo','public interface CreditPaymentRepository extends JpaRepository<CreditPayment,Long>{', 'public interface CreditPaymentRepository extends JpaRepository<CreditPayment,Long>{\n Optional<CreditPayment> findByRequestId(String requestId);')
replace_once('controller','model.addAttribute("paymentForm", new PaymentForm());', 'PaymentForm form = new PaymentForm();\n        form.setRequestId(java.util.UUID.randomUUID().toString());\n        model.addAttribute("paymentForm", form);')
replace_once('html','<form th:action="@{/payments/save}" th:object="${paymentForm}" method="post">', '<form th:action="@{/payments/save}" th:object="${paymentForm}" method="post"><input type="hidden" th:field="*{requestId}">')
replace_once('oldtest','        form.setCustomerId(customerId);', '        form.setRequestId(java.util.UUID.randomUUID().toString());\n        form.setCustomerId(customerId);')
replace_once('service','        requireOpenRemittanceDay(cashier);\n\n        if (form == null ||\n                form.getCustomerId() == null ||', '        if (form == null ||\n                form.getRequestId() == null ||\n                form.getRequestId().isBlank() ||\n                form.getCustomerId() == null ||')
replace_once('service','        Customer customer = customers\n                .findLockedById(form.getCustomerId())', '''        // The cashier row is locked by requireCashier(), serializing
        // retries from the same cashier with remittance submission.
        // Check the key BEFORE the remittance-day check: a retry after
        // a successful payment must not be interpreted as a new payment.
        String requestId;
        try {
            requestId = java.util.UUID.fromString(form.getRequestId()).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payment request ID");
        }
        var previous = payments.findByRequestId(requestId);
        if (previous.isPresent()) {
            CreditPayment existing = previous.get();
            if (existing.getCashier() == null ||
                    !existing.getCashier().getId().equals(cashier.getId()) ||
                    !existing.getCustomer().getId().equals(form.getCustomerId()) ||
                    existing.getAmount().compareTo(form.getAmount()) != 0 ||
                    existing.getMethod() != form.getMethod()) {
                throw new IllegalArgumentException("Payment request ID already used for different payment details");
            }
            return;
        }

        requireOpenRemittanceDay(cashier);

        Customer customer = customers
                .findLockedById(form.getCustomerId())''')
replace_once('service','        payment.assignCashier(cashier);\n\n        payments.save(payment);', '        payment.assignCashier(cashier);\n        payment.assignRequestId(requestId);\n\n        payments.save(payment);')

# Verify every expected edit is in place before writing anything.
for k in ('entity','form','repo','service','controller','html','oldtest'):
 if modified[k]==original[k]: raise SystemExit(f'No change in {k}')

newtest='''package com.smartstock;

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
'''
for k,p in paths.items():
 if k!='newtest': p.write_text(modified[k],encoding='utf-8')
paths['newtest'].write_text(newtest,encoding='utf-8')
print('Updated seven existing files and created PaymentIdempotencyTest.java.')
print('No database was accessed. Run: mvn test')
