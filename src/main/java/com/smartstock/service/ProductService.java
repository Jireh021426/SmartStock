
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private static final String LEGACY_CODE = "LEGACY";

    private final ProductRepository repository;
    private final StockMovementRepository movements;
    private final SaleRepository sales;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository locationInventories;

    // ========================================
    // CONSTRUCTOR
    // ========================================

    public ProductService(
            ProductRepository repository,
            StockMovementRepository movements,
            SaleRepository sales,
            StockLocationRepository locations,
            LocationInventoryRepository locationInventories) {

        this.repository = repository;
        this.movements = movements;
        this.sales = sales;
        this.locations = locations;
        this.locationInventories = locationInventories;
    }

    // ========================================
    // PRODUCT LIST
    // ========================================

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return repository.findAll();
    }

    // ========================================
    // FIND PRODUCT
    // ========================================

    @Transactional(readOnly = true)
    public Product findById(Long id) {

        return repository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found"));
    }

    // ========================================
    // CREATE PRODUCT
    // ========================================

    @Transactional
    public void create(ProductForm form) {

        validateForm(form);

        String sku = form.getSku().trim();

        if (repository.existsBySku(sku)) {

            throw new IllegalArgumentException(
                    "SKU already exists");
        }

        Product product = switch (form.getType()) {

            case "WHOLE" -> new WholeChicken(
                    sku,
                    form.getName(),
                    form.getPrice(),
                    form.getLowStockKg()
            );

            case "PART" -> new ChickenPart(
                    sku,
                    form.getName(),
                    form.getPrice(),
                    form.getLowStockKg()
            );

            default -> throw new IllegalArgumentException(
                    "Invalid chicken product type");
        };

        // New products always begin with zero stock.
        // Purchase stock must enter through InventoryService.
        product.setCostPerKg(form.getCostPerKg());

        repository.saveAndFlush(product);

        // ========================================
        // INITIALIZE ZERO LOCATION BALANCE
        // ========================================

        StockLocation legacy = getOrCreateLegacyLocation();

        LocationInventory balance =
                new LocationInventory(
                        legacy,
                        product
                );

        // Balance starts at 0.000 kg.
        // Do not duplicate company stock.
        locationInventories.save(balance);
    }

    // ========================================
    // UPDATE PRODUCT
    // ========================================

    @Transactional
    public void update(
            Long id,
            ProductForm form) {

        validateForm(form);

        // Lock product to prevent concurrent
        // inventory transactions during editing.
        Product product = repository
                .findLockedById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found"));

        String sku = form.getSku().trim();

        if (repository.existsBySkuAndIdNot(
                sku,
                id)) {

            throw new IllegalArgumentException(
                    "SKU already exists");
        }

        if (!typeOf(product).equals(form.getType())) {

            throw new IllegalArgumentException(
                    "Product type cannot be changed after creation");
        }

        // ========================================
        // PROTECT PURCHASE COST
        // ========================================

        BigDecimal existingCost =
                product.getCostPerKg();

        BigDecimal requestedCost =
                form.getCostPerKg();

        // Once inventory exists, changing cost through
        // the product form would bypass purchase history.
        if (product.getStockKg().signum() > 0 &&
                existingCost.compareTo(requestedCost) != 0) {

            throw new IllegalArgumentException(
                    "Cannot manually change purchase cost " +
                    "while inventory exists. Record new " +
                    "purchases through Stock In instead.");
        }

        // Product details and selling price may change.
        product.update(
                sku,
                form.getName(),
                form.getPrice(),
                form.getLowStockKg()
        );

        // Cost can be set when no stock is held.
        if (product.getStockKg().signum() == 0) {
            product.setCostPerKg(requestedCost);
        }
    }

    // ========================================
    // DELETE PRODUCT
    // ========================================

    @Transactional
    public void delete(Long id) {

        Product product = repository
                .findLockedById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found"));

        // Preserve products referenced by sales
        // or historical stock movements.
        if (sales.existsByProductId(id) ||
                movements.existsByProductId(id)) {

            throw new IllegalArgumentException(
                    "Cannot delete a product with stock " +
                    "history. Keep it for audit purposes.");
        }

        if (product.getStockKg().signum() != 0) {

            throw new IllegalArgumentException(
                    "Stock must be zero before deleting a product.");
        }

        // ========================================
        // CHECK ALL LOCATION BALANCES
        // ========================================

        List<LocationInventory> balances =
                locationInventories.findByProductId(id);

        for (LocationInventory balance : balances) {

            if (balance.getStockKg().signum() != 0) {

                throw new IllegalArgumentException(
                        "Cannot delete product. Stock remains at " +
                        balance.getLocation().getName() +
                        ": " + balance.getStockKg() + " kg");
            }
        }

        // Remove only zero-balance location rows.
        // This is permitted because no historical
        // sales or movements reference the product.
        locationInventories.deleteAll(balances);
        locationInventories.flush();

        repository.delete(product);
    }

    // ========================================
    // VALIDATE PRODUCT FORM
    // ========================================

    private void validateForm(ProductForm form) {

        if (form == null ||
                form.getSku() == null ||
                form.getSku().isBlank() ||
                form.getName() == null ||
                form.getName().isBlank() ||
                form.getType() == null ||
                form.getPrice() == null ||
                form.getPrice().signum() < 0 ||
                form.getPrice().scale() > 2 ||
                form.getLowStockKg() == null ||
                form.getLowStockKg().signum() < 0 ||
                form.getLowStockKg().scale() > 3 ||
                form.getCostPerKg() == null ||
                form.getCostPerKg().signum() < 0 ||
                form.getCostPerKg().scale() > 2) {

            throw new IllegalArgumentException(
                    "Enter valid product information, " +
                    "selling price and purchase cost");
        }

        if (!form.getType().equals("WHOLE") &&
                !form.getType().equals("PART")) {

            throw new IllegalArgumentException(
                    "Invalid chicken product type");
        }
    }

    // ========================================
    // CREATE / REUSE LEGACY LOCATION
    // ========================================

    private StockLocation getOrCreateLegacyLocation() {

        return locations.findByCode(LEGACY_CODE)
                .orElseGet(() ->
                        locations.save(
                                new StockLocation(
                                        LEGACY_CODE,
                                        "Legacy / Unassigned Inventory",
                                        "STORAGE"
                                )
                        )
                );
    }

    // ========================================
    // PRODUCT TYPE
    // ========================================

    public String typeOf(Product product) {

        return product instanceof WholeChicken
                ? "WHOLE"
                : "PART";
    }

    // ========================================
    // CONVERT PRODUCT TO FORM
    // ========================================

    public ProductForm toForm(Product product) {

        ProductForm form = new ProductForm();

        form.setSku(product.getSku());
        form.setName(product.getName());
        form.setPrice(product.getPrice());
        form.setCostPerKg(product.getCostPerKg());
        form.setLowStockKg(product.getLowStockKg());
        form.setType(typeOf(product));

        return form;
    }

    // ========================================
    // TOTAL COMPANY STOCK
    // ========================================

    @Transactional(readOnly = true)
    public BigDecimal totalStockKg() {

        return findAll()
                .stream()
                .map(Product::getStockKg)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    // ========================================
    // TOTAL STOCK SELLING VALUE
    // ========================================

    @Transactional(readOnly = true)
    public BigDecimal totalStockValue() {

        return findAll()
                .stream()
                .map(Product::getStockValue)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }
}
