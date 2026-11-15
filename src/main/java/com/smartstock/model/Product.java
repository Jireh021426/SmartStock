
package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "product_type")
public abstract class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costPerKg =
            BigDecimal.ZERO.setScale(2);

    // Company-wide inventory total.
    // Location-specific balances will be stored separately.
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal stockKg =
            BigDecimal.ZERO.setScale(3);

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal lowStockKg =
            new BigDecimal("5.000");

    protected Product() {
    }

    protected Product(
            String sku,
            String name,
            BigDecimal price,
            BigDecimal lowStockKg) {

        update(sku, name, price, lowStockKg);
    }

    public void update(
            String sku,
            String name,
            BigDecimal price,
            BigDecimal lowStockKg) {

        if (sku == null ||
                sku.isBlank() ||
                name == null ||
                name.isBlank() ||
                price == null ||
                price.signum() < 0 ||
                price.scale() > 2 ||
                lowStockKg == null ||
                lowStockKg.signum() < 0 ||
                lowStockKg.scale() > 3) {

            throw new IllegalArgumentException(
                    "Invalid product information");
        }

        this.sku = sku.trim();
        this.name = name.trim();
        this.price = price;
        this.lowStockKg = lowStockKg.setScale(3);
    }

    // Updates the company-wide stock total.
    public void changeStock(BigDecimal delta) {

        if (delta == null || delta.scale() > 3) {
            throw new IllegalArgumentException(
                    "Weight must have at most 3 decimal places");
        }

        BigDecimal next = stockKg.add(delta);

        if (next.signum() < 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock: only " +
                    stockKg + " kg available");
        }

        stockKg = next.setScale(3);
    }

    public void setCostPerKg(BigDecimal value) {

        if (value == null ||
                value.signum() < 0 ||
                value.scale() > 2) {

            throw new IllegalArgumentException(
                    "Invalid cost/kg");
        }

        costPerKg = value.setScale(2);
    }

    public BigDecimal getCostPerKg() {
        return costPerKg;
    }

    public BigDecimal getStockCostValue() {

        return stockKg.multiply(costPerKg)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public abstract String getCategory();

    public boolean isLowStock() {
        return stockKg.compareTo(lowStockKg) <= 0;
    }

    public BigDecimal getStockValue() {

        return stockKg.multiply(price)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getStockKg() {
        return stockKg;
    }

    public BigDecimal getLowStockKg() {
        return lowStockKg;
    }
}
