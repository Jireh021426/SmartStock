
package com.smartstock;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.model.Remittance;
import com.smartstock.model.StockLocation;

import com.smartstock.repository.EmployeeRepository;
import com.smartstock.repository.RemittanceRepository;
import com.smartstock.repository.StockLocationRepository;

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
import java.util.List;
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
public class RemittanceConcurrencyTest {

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

    private String cashierUsername;
    private Long cashierId;

    @BeforeEach
    void setUp() throws Exception {

        // Confirm that this is the isolated test database.
        try (Connection connection = dataSource.getConnection()) {

            String url = connection
                    .getMetaData()
                    .getURL();

            assertNotNull(url);

            assertTrue(
                    url.startsWith(
                            "jdbc:h2:mem:smartstock_test"
                    ),
                    "Refusing to use unsafe database: " + url
            );

            assertFalse(
                    url.contains("smartstock-chicken-phase3"),
                    "Production database must never be used"
            );
        }

        // Every test receives its own unique records.
        // No existing records are deleted.
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        StockLocation stall = locations.save(
                new StockLocation(
                        "RC" + suffix,
                        "Remittance Concurrency Test Stall",
                        "STALL"
                )
        );

        Employee cashier = employees.save(
                new Employee(
                        "Remittance Concurrency Cashier",
                        "concurrency_cashier_" + suffix,
                        "test-password-hash",
                        EmployeeRole.CASHIER,
                        stall
                )
        );

        cashierUsername = cashier.getUsername();
        cashierId = cashier.getId();
    }

    @Test
    void concurrentSubmissionsMustCreateExactlyOneRemittance()
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

                if (!start.await(
                        10,
                        TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "First thread timed out"
                    );
                }

                return attemptSubmission();
            });

            Future<Boolean> second = executor.submit(() -> {

                ready.countDown();

                if (!start.await(
                        10,
                        TimeUnit.SECONDS
                )) {
                    throw new IllegalStateException(
                            "Second thread timed out"
                    );
                }

                return attemptSubmission();
            });

            assertTrue(
                    ready.await(
                            10,
                            TimeUnit.SECONDS
                    ),
                    "Both threads must be ready"
            );

            start.countDown();

            boolean firstSucceeded =
                    first.get(
                            20,
                            TimeUnit.SECONDS
                    );

            boolean secondSucceeded =
                    second.get(
                            20,
                            TimeUnit.SECONDS
                    );

            int successfulSubmissions =
                    (firstSucceeded ? 1 : 0)
                    + (secondSucceeded ? 1 : 0);

            assertEquals(
                    1,
                    successfulSubmissions,
                    "Exactly one submission must succeed"
            );

            // Check only this cashier's remittances.
            // Other tests may have their own records.
            List<Remittance> saved =
                    remittances
                            .findByCashierIdOrderBySubmittedAtDesc(
                                    cashierId
                            );

            assertEquals(
                    1,
                    saved.size(),
                    "Exactly one remittance must exist for this cashier"
            );

            Remittance result = saved.get(0);

            assertEquals(
                    cashierId,
                    result.getCashier().getId()
            );

            assertEquals(
                    LocalDate.now(),
                    result.getBusinessDate()
            );

            assertEquals(
                    Remittance.Status.SUBMITTED,
                    result.getStatus()
            );

            assertMoneyZero(result.getExpectedCash());
            assertMoneyZero(result.getExpectedGcash());
            assertMoneyZero(result.getDeclaredCash());
            assertMoneyZero(result.getDeclaredGcash());
            assertMoneyZero(result.getCashVariance());
            assertMoneyZero(result.getGcashVariance());

            System.out.println(
                    "PASS: One submission succeeded."
            );

            System.out.println(
                    "PASS: Competing submission was rejected."
            );

            System.out.println(
                    "PASS: Exactly one remittance was stored for this cashier."
            );

            System.out.println(
                    "PASS: Expected and declared totals are correct."
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

    private boolean attemptSubmission() {

        try {

            remittanceService.submit(
                    cashierUsername,
                    new BigDecimal("0.00"),
                    new BigDecimal("0.00")
            );

            return true;

        } catch (IllegalArgumentException exception) {

            // Only the expected duplicate submission
            // is considered an acceptable rejection.
            if ("Today's remittance already submitted"
                    .equals(exception.getMessage())) {

                return false;
            }

            throw exception;
        }
    }

    private void assertMoneyZero(BigDecimal amount) {

        assertNotNull(
                amount,
                "Financial amount must not be null"
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(amount),
                "Financial amount must equal zero"
        );
    }
}
