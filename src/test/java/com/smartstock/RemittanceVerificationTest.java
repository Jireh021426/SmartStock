
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
import java.time.LocalDate;
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
public class RemittanceVerificationTest {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    @Autowired private DataSource dataSource;
    @Autowired private EmployeeRepository employees;
    @Autowired private StockLocationRepository locations;
    @Autowired private RemittanceRepository remittances;
    @Autowired private RemittanceService remittanceService;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String managerUsername;
    private String adminUsername;
    private String cashierUsername;
    private Long cashierId;
    private Long remittanceId;

    @BeforeEach
    void setUp() throws Exception {

        try (Connection connection = dataSource.getConnection()) {

            String url = connection.getMetaData().getURL();

            assertTrue(
                    url.startsWith("jdbc:h2:mem:smartstock_test"),
                    "Unsafe database URL: " + url
            );
        }

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        StockLocation stall = locations.save(
                new StockLocation(
                        "RV" + suffix,
                        "Verification Test Stall",
                        "STALL"
                )
        );

        Employee cashier = employees.save(
                new Employee(
                        "Verification Cashier",
                        "cashier_rv_" + suffix,
                        "test-password-hash",
                        EmployeeRole.CASHIER,
                        stall
                )
        );

        Employee manager = employees.save(
                new Employee(
                        "Verification Manager",
                        "manager_rv_" + suffix,
                        "test-password-hash",
                        EmployeeRole.MANAGER,
                        null
                )
        );

        Employee admin = employees.save(
                new Employee(
                        "Verification Admin",
                        "admin_rv_" + suffix,
                        "test-password-hash",
                        EmployeeRole.ADMIN,
                        null
                )
        );

        cashierUsername = cashier.getUsername();
        managerUsername = manager.getUsername();
        adminUsername = admin.getUsername();
        cashierId = cashier.getId();

        // No sales or repayments for this unique cashier,
        // so expected cash and GCash are both zero.
        Remittance remittance = remittanceService.submit(
                cashierUsername,
                ZERO,
                ZERO
        );

        remittanceId = remittance.getId();
    }

    @Test
    void matchingAmountsMustBeVerified() {

        remittanceService.verify(
                managerUsername,
                remittanceId,
                ZERO,
                ZERO,
                "Collections match"
        );

        Remittance reviewed = reload();

        assertEquals(
                Remittance.Status.VERIFIED,
                reviewed.getStatus()
        );

        assertMoney(ZERO, reviewed.getConfirmedCash());
        assertMoney(ZERO, reviewed.getConfirmedGcash());
        assertMoney(ZERO, reviewed.getConfirmedCashVariance());
        assertMoney(ZERO, reviewed.getConfirmedGcashVariance());

        assertNotNull(reviewed.getVerifiedAt());
        assertNotNull(reviewed.getVerifiedBy());

        assertEquals(
                managerUsername,
                reviewed.getVerifiedBy().getUsername()
        );

        assertEquals(
                "Collections match",
                reviewed.getReviewNote()
        );

        System.out.println(
                "PASS: Matching remittance verified."
        );
    }

    @Test
    void cashDiscrepancyMustBeFlagged() {

        remittanceService.verify(
                managerUsername,
                remittanceId,
                new BigDecimal("100.00"),
                ZERO,
                "Unexpected cash"
        );

        Remittance reviewed = reload();

        assertEquals(
                Remittance.Status.FLAGGED,
                reviewed.getStatus()
        );

        assertMoney(
                new BigDecimal("100.00"),
                reviewed.getConfirmedCashVariance()
        );

        assertMoney(
                new BigDecimal("100.00"),
                reviewed.getCashDeclarationDifference()
        );

        System.out.println(
                "PASS: Cash discrepancy flagged."
        );
    }

    @Test
    void gcashDiscrepancyMustBeFlagged() {

        remittanceService.verify(
                adminUsername,
                remittanceId,
                ZERO,
                new BigDecimal("75.00"),
                "Unexpected GCash"
        );

        Remittance reviewed = reload();

        assertEquals(
                Remittance.Status.FLAGGED,
                reviewed.getStatus()
        );

        assertMoney(
                new BigDecimal("75.00"),
                reviewed.getConfirmedGcashVariance()
        );

        assertEquals(
                adminUsername,
                reviewed.getVerifiedBy().getUsername()
        );

        System.out.println(
                "PASS: GCash discrepancy flagged."
        );
    }

    @Test
    void cashierMustNotVerifyRemittance() {

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> remittanceService.verify(
                        cashierUsername,
                        remittanceId,
                        ZERO,
                        ZERO,
                        "Unauthorized review"
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "Manager approval required"
                )
        );

        assertEquals(
                Remittance.Status.SUBMITTED,
                reload().getStatus()
        );

        System.out.println(
                "PASS: Cashier verification rejected."
        );
    }

    @Test
    void repeatedVerificationMustBeRejected() {

        remittanceService.verify(
                managerUsername,
                remittanceId,
                ZERO,
                ZERO,
                "Original review"
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> remittanceService.verify(
                        adminUsername,
                        remittanceId,
                        ZERO,
                        ZERO,
                        "Second review"
                )
        );

        assertTrue(
                error.getMessage().contains(
                        "already reviewed"
                )
        );

        Remittance reviewed = reload();

        assertEquals(
                Remittance.Status.VERIFIED,
                reviewed.getStatus()
        );

        assertEquals(
                "Original review",
                reviewed.getReviewNote()
        );

        assertEquals(
                managerUsername,
                reviewed.getVerifiedBy().getUsername()
        );

        System.out.println(
                "PASS: Duplicate verification rejected."
        );
    }

    @Test
    void invalidConfirmationMustBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> remittanceService.verify(
                        managerUsername,
                        remittanceId,
                        new BigDecimal("-1.00"),
                        ZERO,
                        "Invalid amount"
                )
        );

        assertEquals(
                Remittance.Status.SUBMITTED,
                reload().getStatus()
        );

        System.out.println(
                "PASS: Negative confirmation rejected."
        );
    }

    @Test
    void overlongNoteMustBeRejected() {

        String note = "X".repeat(1001);

        assertThrows(
                IllegalArgumentException.class,
                () -> remittanceService.verify(
                        managerUsername,
                        remittanceId,
                        ZERO,
                        ZERO,
                        note
                )
        );

        assertEquals(
                Remittance.Status.SUBMITTED,
                reload().getStatus()
        );

        System.out.println(
                "PASS: Overlong review note rejected."
        );
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
