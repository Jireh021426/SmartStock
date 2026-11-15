
package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.RemittanceService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
public class PaymentRemittanceConcurrencyTest {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    private static final BigDecimal PAYMENT =
            new BigDecimal("500.00");

    private static final BigDecimal STARTING_BALANCE =
            new BigDecimal("1000.00");

    @Autowired private DataSource dataSource;
    @Autowired private EmployeeRepository employees;
    @Autowired private StockLocationRepository locations;
    @Autowired private CustomerRepository customers;
    @Autowired private CreditPaymentRepository payments;
    @Autowired private RemittanceRepository remittances;
    @Autowired private SalesService salesService;
    @Autowired private RemittanceService remittanceService;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String cashierUsername;
    private Long cashierId;
    private Long customerId;

    @BeforeEach
    void setUp() throws Exception {

        // Refuse to run against the production database.
        try (Connection connection = dataSource.getConnection()) {

            String url = connection.getMetaData().getURL();

            assertTrue(
                    url.startsWith("jdbc:h2:mem:smartstock_test"),
                    "Unsafe database URL: " + url
            );
        }

        // Use unique test records. No destructive cleanup.
        String suffix = Long.toString(System.nanoTime());

        StockLocation stall = locations.save(
                new StockLocation(
                        "P" + suffix,
                        "Payment Race Test Stall",
                        "STALL"
                )
        );

        Employee cashier = employees.save(
                new Employee(
                        "Payment Race Cashier",
                        "payment_cashier_" + suffix,
                        "test-password-hash",
                        EmployeeRole.CASHIER,
                        stall
                )
        );

        Customer customer = new Customer(
                "Payment Race Customer",
                "09" + suffix
        );

        customer.charge(STARTING_BALANCE);
        customer = customers.save(customer);

        cashierUsername = cashier.getUsername();
        cashierId = cashier.getId();
        customerId = customer.getId();
    }

    private PaymentForm paymentForm() {

        PaymentForm form = new PaymentForm();

        form.setCustomerId(customerId);
        form.setAmount(PAYMENT);
        form.setMethod(PaymentMethod.CASH);

        return form;
    }

    @Test
    void repaymentFirstMustBeIncludedInRemittance() {

        salesService.recordPayment(
                cashierUsername,
                paymentForm()
        );

        remittanceService.submit(
                cashierUsername,
                PAYMENT,
                ZERO
        );

        verifyFinalState(true);

        System.out.println(
                "PASS: Repayment-first remittance includes P500."
        );
    }

    @Test
    void remittanceFirstMustRejectRepayment() {

        remittanceService.submit(
                cashierUsername,
                ZERO,
                ZERO
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> salesService.recordPayment(
                        cashierUsername,
                        paymentForm()
                )
        );

        assertTrue(
                error.getMessage() != null &&
                error.getMessage().contains(
                        "remittance is already submitted"
                )
        );

        verifyFinalState(false);

        System.out.println(
                "PASS: Remittance-first repayment rejected."
        );
    }

    @Test
    void simultaneousRepaymentAndRemittanceMustRemainConsistent()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {

            Future<Boolean> paymentFuture =
                    executor.submit(() -> {

                        ready.countDown();

                        if (!start.await(
                                10, TimeUnit.SECONDS)) {

                            throw new IllegalStateException(
                                    "Payment thread timed out"
                            );
                        }

                        try {

                            salesService.recordPayment(
                                    cashierUsername,
                                    paymentForm()
                            );

                            return true;

                        } catch (IllegalStateException e) {

                            if (e.getMessage() != null &&
                                    e.getMessage().contains(
                                            "remittance is already submitted"
                                    )) {

                                return false;
                            }

                            throw e;
                        }
                    });

            Future<Boolean> remittanceFuture =
                    executor.submit(() -> {

                        ready.countDown();

                        if (!start.await(
                                10, TimeUnit.SECONDS)) {

                            throw new IllegalStateException(
                                    "Remittance thread timed out"
                            );
                        }

                        remittanceService.submit(
                                cashierUsername,
                                ZERO,
                                ZERO
                        );

                        return true;
                    });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both threads must be ready"
            );

            start.countDown();

            boolean paymentSucceeded =
                    paymentFuture.get(
                            20, TimeUnit.SECONDS
                    );

            assertTrue(
                    remittanceFuture.get(
                            20, TimeUnit.SECONDS
                    )
            );

            verifyFinalState(paymentSucceeded);

            System.out.println(
                    "PASS: Concurrent repayment/remittance state is consistent."
            );

        } finally {

            start.countDown();
            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            10, TimeUnit.SECONDS
                    ),
                    "Executor did not terminate"
            );
        }
    }

    private void verifyFinalState(boolean paymentSucceeded) {

        List<CreditPayment> recordedPayments =
                payments.findAll()
                        .stream()
                        .filter(p ->
                                p.getCashier() != null &&
                                cashierId.equals(
                                        p.getCashier().getId()
                                )
                        )
                        .toList();

        assertEquals(
                paymentSucceeded ? 1 : 0,
                recordedPayments.size(),
                "Unexpected number of repayments"
        );

        Remittance remittance = remittances
                .findByCashierIdAndBusinessDate(
                        cashierId,
                        LocalDate.now()
                )
                .orElseThrow(() ->
                        new AssertionError(
                                "Remittance missing"
                        )
                );

        BigDecimal expectedCash =
                paymentSucceeded ? PAYMENT : ZERO;

        assertAmount(
                expectedCash,
                remittance.getExpectedCash()
        );

        assertAmount(
                ZERO,
                remittance.getExpectedGcash()
        );

        assertEquals(
                Remittance.Status.SUBMITTED,
                remittance.getStatus()
        );

        Customer customer = customers
                .findById(customerId)
                .orElseThrow();

        BigDecimal expectedBalance =
                paymentSucceeded
                        ? new BigDecimal("500.00")
                        : STARTING_BALANCE;

        assertAmount(
                expectedBalance,
                customer.getBalance()
        );

        if (paymentSucceeded) {

            assertAmount(
                    PAYMENT,
                    recordedPayments.get(0).getAmount()
            );

            assertEquals(
                    PaymentMethod.CASH,
                    recordedPayments.get(0).getMethod()
            );
        }

        System.out.println(
                "PASS: Customer balance and remittance totals agree."
        );
    }

    private void assertAmount(
            BigDecimal expected,
            BigDecimal actual) {

        assertNotNull(actual);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Financial amount mismatch"
        );
    }
}
