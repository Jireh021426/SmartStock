
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
public class ConcurrentRemittanceVerificationTest {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private StockLocationRepository locations;

    @Autowired
    private RemittanceRepository remittances;

    @Autowired
    private RemittanceService remittanceService;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String firstManagerUsername;
    private String secondManagerUsername;
    private Long remittanceId;

    @BeforeEach
    void setUp() throws Exception {

        // Verify database isolation before any writes.
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

        // Unique records avoid interference with other tests.
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        StockLocation stall = locations.save(
                new StockLocation(
                        "CV" + suffix,
                        "Concurrent Verification Stall",
                        "STALL"
                )
        );

        Employee cashier = employees.save(
                new Employee(
                        "Concurrent Verification Cashier",
                        "cv_cashier_" + suffix,
                        "test-password-hash",
                        EmployeeRole.CASHIER,
                        stall
                )
        );

        Employee firstManager = employees.save(
                new Employee(
                        "First Verification Manager",
                        "cv_manager1_" + suffix,
                        "test-password-hash",
                        EmployeeRole.MANAGER,
                        null
                )
        );

        Employee secondManager = employees.save(
                new Employee(
                        "Second Verification Manager",
                        "cv_manager2_" + suffix,
                        "test-password-hash",
                        EmployeeRole.MANAGER,
                        null
                )
        );

        firstManagerUsername = firstManager.getUsername();
        secondManagerUsername = secondManager.getUsername();

        Remittance submitted = remittanceService.submit(
                cashier.getUsername(),
                ZERO,
                ZERO
        );

        remittanceId = submitted.getId();
    }

    @Test
    void concurrentVerificationMustAllowExactlyOneManager()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        try {

            Future<Boolean> first = executor.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "First manager timed out"
                    );
                }

                return attemptVerification(
                        firstManagerUsername,
                        "First manager review"
                );
            });

            Future<Boolean> second = executor.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Second manager timed out"
                    );
                }

                return attemptVerification(
                        secondManagerUsername,
                        "Second manager review"
                );
            });

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both managers must be ready"
            );

            start.countDown();

            boolean firstSucceeded =
                    first.get(20, TimeUnit.SECONDS);

            boolean secondSucceeded =
                    second.get(20, TimeUnit.SECONDS);

            int successfulVerifications =
                    (firstSucceeded ? 1 : 0)
                    + (secondSucceeded ? 1 : 0);

            assertEquals(
                    1,
                    successfulVerifications,
                    "Exactly one manager must verify the remittance"
            );

            Remittance result = remittances.findById(
                    remittanceId
            ).orElseThrow();

            assertEquals(
                    Remittance.Status.VERIFIED,
                    result.getStatus()
            );

            assertNotNull(result.getVerifiedBy());
            assertNotNull(result.getVerifiedAt());

            if (firstSucceeded) {

                assertEquals(
                        firstManagerUsername,
                        result.getVerifiedBy().getUsername()
                );

                assertEquals(
                        "First manager review",
                        result.getReviewNote()
                );

            } else {

                assertEquals(
                        secondManagerUsername,
                        result.getVerifiedBy().getUsername()
                );

                assertEquals(
                        "Second manager review",
                        result.getReviewNote()
                );
            }

            assertMoneyZero(result.getConfirmedCash());
            assertMoneyZero(result.getConfirmedGcash());

            System.out.println(
                    "PASS: Exactly one manager verified the remittance."
            );

            System.out.println(
                    "PASS: Competing manager verification rejected."
            );

            System.out.println(
                    "PASS: Winning manager identity preserved."
            );

            System.out.println(
                    "PASS: Original verification note preserved."
            );

        } finally {

            start.countDown();

            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    ),
                    "Executor did not terminate"
            );
        }
    }

    private boolean attemptVerification(
            String username,
            String note) {

        try {

            remittanceService.verify(
                    username,
                    remittanceId,
                    ZERO,
                    ZERO,
                    note
            );

            return true;

        } catch (IllegalStateException exception) {

            if ("Remittance already reviewed"
                    .equals(exception.getMessage())) {

                return false;
            }

            throw exception;
        }
    }

    private void assertMoneyZero(BigDecimal amount) {

        assertNotNull(amount);

        assertEquals(
                0,
                ZERO.compareTo(amount),
                "Expected zero financial amount"
        );
    }
}
