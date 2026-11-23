
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class InventoryService {

    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of("Asia/Manila");

    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository locationInventories;

    public InventoryService(
            SupplierRepository suppliers,
            ProductRepository products,
            StockMovementRepository movements,
            StockLocationRepository locations,
            LocationInventoryRepository locationInventories) {

        this.suppliers = suppliers;
        this.products = products;
        this.movements = movements;
        this.locations = locations;
        this.locationInventories = locationInventories;
    }

    @Transactional(readOnly = true)
    public List<StockMovement> history() {
        return movements.findAllByOrderByRecordedAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> history(Pageable pageable) {
        return movements.findAllByOrderByRecordedAtDescIdDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long totalMovements() {
        return movements.count();
    }

    @Transactional(readOnly = true)
    public long countByType(MovementType type) {
        return movements.countByType(type);
    }

    @Transactional
    public void record(MovementForm form) {

        if (form == null ||
                form.getProductId() == null ||
                form.getLocationId() == null ||
                form.getType() == null ||
                form.getBusinessDate() == null ||
                form.getWeightKg() == null ||
                form.getWeightKg().signum() <= 0 ||
                form.getWeightKg().scale() > 3 ||
                form.getReason() == null ||
                form.getReason().isBlank() ||
                form.getReason().trim().length() > 255) {

            throw new IllegalArgumentException(
                    "Enter a valid product, location, business date, " +
                    "movement, weight and reason");
        }

        LocalDate businessDate = form.getBusinessDate();

        if (businessDate.isAfter(LocalDate.now(BUSINESS_ZONE))) {
            throw new IllegalArgumentException(
                    "Business date cannot be in the future");
        }

        BigDecimal weight = form.getWeightKg().setScale(3);

        // Lock product first to serialize inventory changes.
        Product product = products
                .findLockedById(form.getProductId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product not found"));

        StockLocation location = locations
                .findById(form.getLocationId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Stock location not found"));

        if (!location.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot record inventory at an inactive location");
        }

        LocationInventory balance = locationInventories
                .findLockedByLocationAndProduct(
                        location.getId(),
                        product.getId())
                .orElse(null);

        if (form.getType() == MovementType.STOCK_IN) {

            // Validate supplier and receipt BEFORE adjusting stock balances.
            if (form.getSupplierId() == null) {
                throw new IllegalArgumentException("Select a supplier for stock-in");
            }
            Supplier supplier = suppliers.findById(form.getSupplierId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
            if (!supplier.isActive()) {
                throw new IllegalArgumentException("Supplier is inactive");
            }
            String receiptNumber = form.getDeliveryReceiptNumber();
            if (receiptNumber == null || receiptNumber.isBlank()
                    || receiptNumber.trim().length() > 80) {
                throw new IllegalArgumentException("Delivery receipt number must contain 1-80 characters");
            }

            BigDecimal incomingCost =
                    form.getPurchaseCostPerKg();

            if (incomingCost == null ||
                    incomingCost.signum() <= 0 ||
                    incomingCost.scale() > 2) {

                throw new IllegalArgumentException(
                        "Stock-in requires a positive purchase cost per kg");
            }

            BigDecimal existingKg = product.getStockKg();
            BigDecimal existingCost = product.getCostPerKg();

            if (existingKg == null ||
                    existingKg.signum() < 0 ||
                    existingCost == null ||
                    existingCost.signum() < 0) {

                throw new IllegalStateException(
                        "Existing inventory quantity or cost is invalid");
            }

            if (existingKg.signum() > 0 &&
                    existingCost.signum() == 0) {

                throw new IllegalStateException(
                        "Set the existing inventory purchase cost " +
                        "before adding stock");
            }

            if (balance == null) {
                balance = new LocationInventory(
                        location,
                        product
                );
            }

            BigDecimal existingValue =
                    existingKg.multiply(existingCost);

            BigDecimal incomingValue =
                    weight.multiply(incomingCost);

            BigDecimal newTotalKg =
                    existingKg.add(weight);

            BigDecimal averageCost =
                    existingValue.add(incomingValue)
                            .divide(
                                    newTotalKg,
                                    2,
                                    RoundingMode.HALF_UP
                            );

            balance.changeStock(weight);
            product.changeStock(weight);
            product.setCostPerKg(averageCost);

            locationInventories.save(balance);

            StockMovement movement = new StockMovement(
                    product,
                    MovementType.STOCK_IN,
                    weight,
                    form.getReason().trim()
            );

            movement.assignLocation(location);
            movement.assignSupplierDelivery(supplier, receiptNumber);
            movement.assignCategory(MovementCategory.PURCHASE);

            // Record the actual business date separately
            // from the timestamp when the user submitted it.
            movement.assignBusinessDate(businessDate);

            movement.setPurchaseDetails(
                    incomingCost,
                    averageCost
            );

            movements.save(movement);

        } else if (form.getType() == MovementType.STOCK_OUT) {

            MovementCategory category =
                    form.getCategory();

            if (category != MovementCategory.SPOILAGE &&
                    category != MovementCategory.DAMAGE &&
                    category != MovementCategory.MISSING_STOCK) {

                throw new IllegalArgumentException(
                        "Select spoilage, damage or missing stock " +
                        "for manual stock-out");
            }

            if (balance == null) {
                throw new IllegalArgumentException(
                        "No inventory exists for this product at " +
                        location.getName());
            }

            if (balance.getStockKg().compareTo(weight) < 0) {
                throw new IllegalArgumentException(
                        "Insufficient stock at " +
                        location.getName() +
                        ". Available: " +
                        balance.getStockKg() + " kg");
            }

            BigDecimal existingCost =
                    product.getCostPerKg();

            if (existingCost == null ||
                    existingCost.signum() <= 0) {

                throw new IllegalStateException(
                        "Set a valid inventory purchase cost " +
                        "before recording stock loss");
            }

            balance.changeStock(weight.negate());
            product.changeStock(weight.negate());

            locationInventories.save(balance);

            StockMovement movement = new StockMovement(
                    product,
                    MovementType.STOCK_OUT,
                    weight,
                    form.getReason().trim()
            );

            movement.assignLocation(location);
            movement.assignCategory(category);
            movement.assignBusinessDate(businessDate);
            movement.setInventoryLossValue(existingCost);

            movements.save(movement);

        } else {
            throw new IllegalArgumentException(
                    "Unsupported stock movement type");
        }
    }
}
