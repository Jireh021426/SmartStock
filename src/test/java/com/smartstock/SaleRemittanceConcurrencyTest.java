
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
public class SaleRemittanceConcurrencyTest {

    private static final String CASHIER =
            "sale_race_cashier";

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    private static final BigDecimal SALE_TOTAL =
            new BigDecimal("390.00");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private StockLocationRepository locations;

    @Autowired
    private ProductRepository products;

    @Autowired
    private LocationInventoryRepository inventories;

    @Autowired
    private SaleRepository sales;

    @Autowired
    private RemittanceRepository remittances;

    @Autowired
    private SalesService salesService;

    @Autowired
    private RemittanceService remittanceService;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private Long cashierId;
    private Long locationId;
    private Long productId;

    @BeforeEach
    void setUp() throws Exception {

        // Never permit test cleanup against production.
        try (Connection connection = dataSource.getConnection()) {

            String url = connection
                    .getMetaData()
                    .getURL();

            assertTrue(
                    url.startsWith(
                            "jdbc:h2:mem:smartstock_test"
                    ),
                    "Unsafe database URL: " + url
            );
        }

        remittances.deleteAll();
        sales.deleteAll();

        // Each test uses a new, unique set of records.
        // This avoids deleting unrelated inventory,
        // movement, or historical test records.
        String suffix =
                Long.toString(System.nanoTime());

        StockLocation stall = locations.save(
                new StockLocation(
                        "S" + suffix,
                        "Sale Race Test Stall",
                        "STALL"
                )
        );

        Employee cashier = employees.save(
                new Employee(
                        "Sale Race Cashier",
                        CASHIER + suffix,
                        "test-password-hash",
                        EmployeeRole.CASHIER,
                        stall
                )
        );

        WholeChicken chicken = new WholeChicken(
                "WC" + suffix,
                "Test Whole Chicken",
                new BigDecimal("195.00"),
                new BigDecimal("2.000")
        );

        chicken.setCostPerKg(
                new BigDecimal("180.50")
        );

        chicken.changeStock(
                new BigDecimal("10.000")
        );

        chicken = products.save(chicken);

        LocationInventory inventory =
                new LocationInventory(
                        stall,
                        chicken
                );

        inventory.changeStock(
                new BigDecimal("10.000")
        );

        inventories.save(inventory);

        cashierId = cashier.getId();
        locationId = stall.getId();
        productId = chicken.getId();

        // The username must match the unique test cashier.
        testUsername = cashier.getUsername();
    }

    private String testUsername;

    private SaleForm saleForm() {

        SaleForm form = new SaleForm();

        form.setProductId(productId);
        form.setLocationId(locationId);
        form.setWeightKg(
                new BigDecimal("2.000")
        );
        form.setPaymentMethod(
                PaymentMethod.CASH
        );

        return form;
    }

    @Test
    void saleFirstMustBeIncludedInRemittance() {

        salesService.recordSale(
                testUsername,
                saleForm()
        );

        remittanceService.submit(
                testUsername,
                SALE_TOTAL,
                ZERO
        );

        verifyFinalState(true);

        System.out.println(
                "PASS: Sale-first remittance includes P390."
        );
    }

    @Test
    void remittanceFirstMustRejectSale() {

        remittanceService.submit(
                testUsername,
                ZERO,
                ZERO
        );

        IllegalStateException error =
                assertThrows(
                        IllegalStateException.class,
                        () -> salesService.recordSale(
                                testUsername,
                                saleForm()
                        )
                );

        assertTrue(
                error.getMessage().contains(
                        "remittance is already submitted"
                )
        );

        verifyFinalState(false);

        System.out.println(
                "PASS: Remittance-first sale was rejected."
        );
    }

    @Test
    void simultaneousSaleAndRemittanceMustRemainConsistent()
            throws Exception {

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        try {

            Future<Boolean> saleFuture =
                    executor.submit(() -> {

                        ready.countDown();

                        if (!start.await(
                                10,
                                TimeUnit.SECONDS
                        )) {
                            throw new IllegalStateException(
                                    "Sale thread timed out"
                            );
                        }

                        try {

                            salesService.recordSale(
                                    testUsername,
                                    saleForm()
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
                                10,
                                TimeUnit.SECONDS
                        )) {
                            throw new IllegalStateException(
                                    "Remittance thread timed out"
                            );
                        }

                        remittanceService.submit(
                                testUsername,
                                ZERO,
                                ZERO
                        );

                        return true;
                    });

            assertTrue(
                    ready.await(
                            10,
                            TimeUnit.SECONDS
                    ),
                    "Both threads must be ready"
            );

            start.countDown();

            boolean saleSucceeded =
                    saleFuture.get(
                            20,
                            TimeUnit.SECONDS
                    );

            assertTrue(
                    remittanceFuture.get(
                            20,
                            TimeUnit.SECONDS
                    )
            );

            verifyFinalState(saleSucceeded);

            System.out.println(
                    "PASS: Concurrent sale/remittance state is consistent."
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

    private void verifyFinalState(boolean saleSucceeded) {

        List<Sale> recordedSales = sales.findAll()
                .stream()
                .filter(s ->
                        s.getCashier() != null &&
                        cashierId.equals(
                                s.getCashier().getId()
                        )
                )
                .toList();

        assertEquals(
                saleSucceeded ? 1 : 0,
                recordedSales.size(),
                "Unexpected number of recorded sales"
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
                saleSucceeded
                        ? SALE_TOTAL
                        : ZERO;

        assertMoneyEquals(
                expectedCash,
                remittance.getExpectedCash()
        );

        BigDecimal expectedStock =
                saleSucceeded
                        ? new BigDecimal("8.000")
                        : new BigDecimal("10.000");

        Product product = products.findById(productId)
                .orElseThrow();

        LocationInventory inventory = inventories
                .findByLocationIdAndProductId(
                        locationId,
                        productId
                )
                .orElseThrow();

        assertEquals(
                0,
                expectedStock.compareTo(
                        product.getStockKg()
                ),
                "Company stock mismatch"
        );

        assertEquals(
                0,
                expectedStock.compareTo(
                        inventory.getStockKg()
                ),
                "Stall stock mismatch"
        );

        assertMoneyEquals(
                ZERO,
                remittance.getExpectedGcash()
        );

        assertEquals(
                Remittance.Status.SUBMITTED,
                remittance.getStatus()
        );

        if (saleSucceeded) {

            assertMoneyEquals(
                    SALE_TOTAL,
                    recordedSales.get(0).getTotal()
            );
        }
    }

    private void assertMoneyEquals(
            BigDecimal expected,
            BigDecimal actual) {

        assertNotNull(actual);

        assertEquals(
                0,
                expected.compareTo(actual),
                "Money amount mismatch"
        );
    }
}
