
package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.RemittanceService;

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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
public class PostSubmissionTamperingTest {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    private static final BigDecimal PAYMENT =
            new BigDecimal("500.00");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private StockLocationRepository locations;

    @Autowired
    private CustomerRepository customers;

    @Autowired
    private CreditPaymentRepository payments;

    @Autowired
    private RemittanceRepository remittances;

    @Autowired
    private RemittanceService remittanceService;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private Employee cashier;
    private Customer customer;

    private String managerUsername;
    private Long remittanceId;

    @BeforeEach
    void setUp() throws Exception {

        // Never run this test against the production database.
        try (Connection connection = dataSource.getConnection()) {

            String url = connection.getMetaData().getURL();

            assertNotNull(url);

            assertTrue(
                    url.startsWith("jdbc:h2:mem:smartstock_test"),
                    "Unsafe database URL: " + url
            );

            assertFalse(
                    url.contains("smartstock-chicken-phase3"),
                    "Production database must never be used"
            );
        }

        // Unique records prevent conflicts with other tests.
        // No existing records are deleted.
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        StockLocation stall = locations.save(
                new StockLocation(
                        "PT" + suffix,
                        "Tampering Test Stall",
                        "STALL"
                )
        );

        cashier = employees.save(
                new Employee(
                        "Tampering Test Cashier",
                        "tamper_cashier_" + suffix,
                        "test-password-hash",
                        EmployeeRole.CASHIER,
                        stall
                )
        );

        Employee manager = employees.save(
                new Employee(
                        "Tampering Test Manager",
                        "tamper_manager_" + suffix,
                        "test-password-hash",
                        EmployeeRole.MANAGER,
                        null
                )
        );

        managerUsername = manager.getUsername();

        customer = new Customer(
                "Tampering Test Customer",
                "09" + suffix
        );

        customer.charge(PAYMENT);
        customer = customers.save(customer);

        // The cashier submits an empty remittance.
        // Expected and declared totals are both zero.
        Remittance submitted = remittanceService.submit(
                cashier.getUsername(),
                ZERO,
                ZERO
        );

        remittanceId = submitted.getId();
    }

    @Test
    void unchangedRemittanceMustBeVerified() {

        remittanceService.verify(
                managerUsername,
                remittanceId,
                ZERO,
                ZERO,
                "No discrepancy"
        );

        Remittance result = reload();

        assertEquals(
                Remittance.Status.VERIFIED,
                result.getStatus()
        );

        assertMoney(ZERO, result.getExpectedCash());
        assertMoney(ZERO, result.getExpectedGcash());

        assertNotNull(result.getVerifiedAt());

        System.out.println(
                "PASS: Untampered remittance verified."
        );
    }

    @Test
    void lateCashRepaymentMustBlockVerification() {

        insertLatePayment(PaymentMethod.CASH);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> remittanceService.verify(
                        managerUsername,
                        remittanceId,
                        ZERO,
                        ZERO,
                        "Attempted approval after cash change"
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "New transactions were recorded after submission"
                )
        );

        Remittance result = reload();

        assertEquals(
                Remittance.Status.SUBMITTED,
                result.getStatus()
        );

        assertMoney(ZERO, result.getExpectedCash());
        assertMoney(ZERO, result.getExpectedGcash());

        assertNull(result.getVerifiedAt());
        assertNull(result.getVerifiedBy());

        assertMoney(
                PAYMENT,
                remittanceService.expected(
                        cashier,
                        result.getBusinessDate()
                ).cash()
        );

        System.out.println(
                "PASS: Late cash repayment blocked verification."
        );
    }

    @Test
    void lateGcashRepaymentMustBlockVerification() {

        insertLatePayment(PaymentMethod.GCASH);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> remittanceService.verify(
                        managerUsername,
                        remittanceId,
                        ZERO,
                        ZERO,
                        "Attempted approval after GCash change"
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "New transactions were recorded after submission"
                )
        );

        Remittance result = reload();

        assertEquals(
                Remittance.Status.SUBMITTED,
                result.getStatus()
        );

        assertMoney(ZERO, result.getExpectedCash());
        assertMoney(ZERO, result.getExpectedGcash());

        assertNull(result.getVerifiedAt());
        assertNull(result.getVerifiedBy());

        assertMoney(
                PAYMENT,
                remittanceService.expected(
                        cashier,
                        result.getBusinessDate()
                ).gcash()
        );

        System.out.println(
                "PASS: Late GCash repayment blocked verification."
        );
    }

    private void insertLatePayment(PaymentMethod method) {

        // This intentionally bypasses SalesService.
        // It simulates an unauthorized or faulty write.
        //
        // Only the isolated in-memory test database is used.

        customer.pay(PAYMENT);
        customer = customers.save(customer);

        CreditPayment payment = new CreditPayment(
                customer,
                PAYMENT,
                method
        );

        payment.assignCashier(cashier);

        payments.saveAndFlush(payment);
    }

    private Remittance reload() {

        return remittances.findById(remittanceId)
                .orElseThrow();
    }

    private void assertMoney(
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
