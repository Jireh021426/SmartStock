
package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
    name = "location_inventory",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_location_product",
            columnNames = {
                "location_id",
                "product_id"
            }
        )
    }
)
public class LocationInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            optional = false,
            fetch = FetchType.LAZY
    )
    @JoinColumn(
            name = "location_id",
            nullable = false
    )
    private StockLocation location;

    @ManyToOne(
            optional = false,
            fetch = FetchType.LAZY
    )
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @Column(
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal stockKg =
            BigDecimal.ZERO.setScale(3);

    protected LocationInventory() {
    }

    public LocationInventory(
            StockLocation location,
            Product product) {

        if (location == null || product == null) {
            throw new IllegalArgumentException(
                "Location and product are required"
            );
        }

        this.location = location;
        this.product = product;
    }

    public void changeStock(BigDecimal delta) {

        if (delta == null || delta.scale() > 3) {
            throw new IllegalArgumentException(
                "Weight must have at most 3 decimal places"
            );
        }

        BigDecimal next = stockKg.add(delta);

        if (next.signum() < 0) {
            throw new IllegalArgumentException(
                "Insufficient location inventory: only " +
                stockKg + " kg available"
            );
        }

        stockKg = next.setScale(3);
    }

    public Long getId() {
        return id;
    }

    public StockLocation getLocation() {
        return location;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getStockKg() {
        return stockKg;
    }
}
