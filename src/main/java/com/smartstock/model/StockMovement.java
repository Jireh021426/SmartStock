
package com.smartstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            optional = false,
            fetch = FetchType.EAGER
    )
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Column(
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal weightKg;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    // ========================================
    // LOCATION TRACKING
    // ========================================

    // Nullable to preserve historical movements.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private StockLocation location;

    // Nullable for historical records.
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_category", length = 40)
    private MovementCategory category;

    // ========================================
    // PURCHASE ACCOUNTING
    // ========================================

    @Column(precision = 12, scale = 2)
    private BigDecimal purchaseCostPerKg;

    @Column(precision = 16, scale = 2)
    private BigDecimal totalPurchaseCost;

    @Column(precision = 12, scale = 2)
    private BigDecimal averageCostAfter;

    // ========================================
    // INVENTORY LOSS
    // ========================================

    @Column(precision = 16, scale = 2)
    private BigDecimal inventoryLossValue;

    // Required by JPA
    protected StockMovement() {
    }

    // Existing constructor retained for compatibility.
    public StockMovement(
            Product product,
            MovementType type,
            BigDecimal weightKg,
            String reason) {

        if (product == null ||
                type == null ||
                weightKg == null ||
                weightKg.signum() <= 0 ||
                weightKg.scale() > 3 ||
                reason == null ||
                reason.isBlank() ||
                reason.trim().length() > 255) {

            throw new IllegalArgumentException(
                    "Invalid stock movement information");
        }

        this.product = product;
        this.type = type;
        this.weightKg = weightKg.setScale(3);
        this.reason = reason.trim();
        this.recordedAt = LocalDateTime.now();
    }

    // ========================================
    // ASSIGN LOCATION
    // ========================================

    public void assignLocation(StockLocation location) {

        if (location == null) {
            throw new IllegalArgumentException(
                    "Stock location is required");
        }

        if (!location.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot use an inactive stock location");
        }

        this.location = location;
    }

    // ========================================
    // ASSIGN CATEGORY
    // ========================================

    public void assignCategory(MovementCategory category) {

        if (category == null) {
            throw new IllegalArgumentException(
                    "Movement category is required");
        }

        if (type == MovementType.STOCK_IN &&
                category != MovementCategory.PURCHASE &&
                category != MovementCategory.INITIAL_STOCK &&
                category != MovementCategory.STOCK_ADJUSTMENT &&
                category != MovementCategory.STOCK_TRANSFER) {

            throw new IllegalArgumentException(
                    "Invalid category for stock-in");
        }

        if (type == MovementType.STOCK_OUT &&
                category != MovementCategory.SALE &&
                category != MovementCategory.SPOILAGE &&
                category != MovementCategory.DAMAGE &&
                category != MovementCategory.MISSING_STOCK &&
                category != MovementCategory.STOCK_ADJUSTMENT &&
                category != MovementCategory.STOCK_TRANSFER) {

            throw new IllegalArgumentException(
                    "Invalid category for stock-out");
        }

        this.category = category;
    }

    // ========================================
    // RECORD PURCHASE DETAILS
    // ========================================

    public void setPurchaseDetails(
            BigDecimal purchaseCostPerKg,
            BigDecimal averageCostAfter) {

        if (type != MovementType.STOCK_IN) {
            throw new IllegalStateException(
                    "Purchase details can only be recorded for stock-in");
        }

        if (purchaseCostPerKg == null ||
                purchaseCostPerKg.signum() <= 0 ||
                averageCostAfter == null ||
                averageCostAfter.signum() < 0) {

            throw new IllegalArgumentException(
                    "Invalid purchase or average cost");
        }

        this.purchaseCostPerKg =
                purchaseCostPerKg.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        this.totalPurchaseCost =
                weightKg.multiply(this.purchaseCostPerKg)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        this.averageCostAfter =
                averageCostAfter.setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    // ========================================
    // RECORD INVENTORY LOSS
    // ========================================

    public void setInventoryLossValue(
            BigDecimal costPerKg) {

        if (type != MovementType.STOCK_OUT) {
            throw new IllegalStateException(
                    "Inventory loss can only be recorded for stock-out");
        }

        if (costPerKg == null ||
                costPerKg.signum() < 0) {

            throw new IllegalArgumentException(
                    "Invalid inventory cost");
        }

        if (category != null &&
                category != MovementCategory.SPOILAGE &&
                category != MovementCategory.DAMAGE &&
                category != MovementCategory.MISSING_STOCK) {

            throw new IllegalStateException(
                    "Inventory loss is not applicable to this category");
        }

        this.inventoryLossValue =
                weightKg.multiply(costPerKg)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );
    }

    // ========================================
    // GETTERS
    // ========================================

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public MovementType getType() {
        return type;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public StockLocation getLocation() {
        return location;
    }

    public MovementCategory getCategory() {
        return category;
    }

    public BigDecimal getPurchaseCostPerKg() {
        return purchaseCostPerKg;
    }

    public BigDecimal getTotalPurchaseCost() {
        return totalPurchaseCost;
    }

    public BigDecimal getAverageCostAfter() {
        return averageCostAfter;
    }

    public BigDecimal getInventoryLossValue() {
        return inventoryLossValue;
    }
}
