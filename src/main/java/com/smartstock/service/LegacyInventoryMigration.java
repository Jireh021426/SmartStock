
package com.smartstock.service;

import com.smartstock.model.LocationInventory;
import com.smartstock.model.Product;
import com.smartstock.model.StockLocation;

import com.smartstock.repository.LocationInventoryRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.StockLocationRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Component
@ConditionalOnProperty(name = "smartstock.migration.enabled", havingValue = "true", matchIfMissing = true)
public class LegacyInventoryMigration
        implements ApplicationRunner {

    private static final String LEGACY_CODE = "LEGACY";

    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository inventories;

    public LegacyInventoryMigration(
            ProductRepository products,
            StockLocationRepository locations,
            LocationInventoryRepository inventories) {

        this.products = products;
        this.locations = locations;
        this.inventories = inventories;
    }

    // ========================================
    // VERIFY AND INITIALIZE INVENTORY
    // ========================================

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        List<Product> allProducts = products.findAll();
        List<LocationInventory> allBalances =
                inventories.findAll();

        Map<Long, BigDecimal> allocatedByProduct =
                new HashMap<>();

        Map<Long, Integer> balanceCountByProduct =
                new HashMap<>();

        // ========================================
        // VALIDATE EXISTING LOCATION BALANCES
        // ========================================

        for (LocationInventory balance : allBalances) {

            if (balance.getProduct() == null ||
                    balance.getLocation() == null ||
                    balance.getStockKg() == null ||
                    balance.getStockKg().signum() < 0) {

                throw new IllegalStateException(
                        "Invalid location inventory record: " +
                        balance.getId());
            }

            Long productId =
                    balance.getProduct().getId();

            allocatedByProduct.merge(
                    productId,
                    balance.getStockKg(),
                    BigDecimal::add
            );

            balanceCountByProduct.merge(
                    productId,
                    1,
                    Integer::sum
            );
        }

        // ========================================
        // VALIDATE COMPANY STOCK
        // ========================================

        for (Product product : allProducts) {

            if (product.getStockKg() == null ||
                    product.getStockKg().signum() < 0) {

                throw new IllegalStateException(
                        "Invalid company inventory for " +
                        product.getSku());
            }

            int count = balanceCountByProduct
                    .getOrDefault(product.getId(), 0);

            BigDecimal allocated = allocatedByProduct
                    .getOrDefault(
                            product.getId(),
                            BigDecimal.ZERO
                    );

            // Existing allocations must match exactly.
            // Never silently correct discrepancies.
            if (count > 0 &&
                    allocated.compareTo(
                            product.getStockKg()) != 0) {

                throw new IllegalStateException(
                        "Inventory mismatch for product " +
                        product.getSku() +
                        ": company stock = " +
                        product.getStockKg() +
                        ", location stock = " +
                        allocated
                );
            }
        }

        // ========================================
        // IDENTIFY PRODUCTS NEEDING INITIALIZATION
        // ========================================

        List<Product> missingProducts =
                allProducts.stream()
                        .filter(product ->
                                !balanceCountByProduct
                                        .containsKey(product.getId()))
                        .toList();

        if (missingProducts.isEmpty()) {

            System.out.println(
                    "SmartStock: All product inventory " +
                    "balances verified. No migration needed."
            );

            return;
        }

        // ========================================
        // CREATE OR REUSE LEGACY LOCATION
        // ========================================

        StockLocation legacy = locations
                .findByCode(LEGACY_CODE)
                .orElseGet(() ->
                        locations.save(
                                new StockLocation(
                                        LEGACY_CODE,
                                        "Legacy / Unassigned Inventory",
                                        "STORAGE"
                                )
                        )
                );

        // ========================================
        // INITIALIZE MISSING PRODUCTS
        // ========================================

        int initialized = 0;

        for (Product product : missingProducts) {

            // Existing nonzero product stock is assigned
            // to LEGACY without changing the company total.
            //
            // New products with zero stock receive
            // a zero-balance record.

            LocationInventory balance =
                    new LocationInventory(
                            legacy,
                            product
                    );

            if (product.getStockKg().signum() > 0) {

                balance.changeStock(
                        product.getStockKg()
                );
            }

            inventories.save(balance);

            initialized++;
        }

        System.out.println(
                "SmartStock: Inventory verified. " +
                initialized +
                " missing product balances initialized."
        );
    }
}
