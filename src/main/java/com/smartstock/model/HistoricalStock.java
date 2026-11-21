package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "historical_stock", uniqueConstraints = {
    @UniqueConstraint(name = "uk_historical_stock_day_product", columnNames = {"day_id", "product_key"})
})
public class HistoricalStock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "day_id", nullable = false)
    private HistoricalDay day;
    @Column(name = "product_name", nullable = false, length = 100) private String productName;
    @Column(name = "product_key", nullable = false, length = 110) private String productKey;
    @Column(name = "unit", nullable = false, length = 5) private String unit;
    @Column(name = "available_qty", nullable = false, precision = 14, scale = 3) private BigDecimal availableQty;
    @Column(name = "closing_qty", nullable = false, precision = 14, scale = 3) private BigDecimal closingQty;
    @Column(name = "known_loss_qty", nullable = false, precision = 14, scale = 3) private BigDecimal knownLossQty;
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2) private BigDecimal sellingPrice;
    protected HistoricalStock() {}
    public HistoricalStock(HistoricalDay day, String productName, String unit, BigDecimal available,
                           BigDecimal closing, BigDecimal knownLoss, BigDecimal price) {
        if (day == null || productName == null || productName.isBlank() || productName.trim().length() > 100 ||
            ( !"KG".equals(unit) && !"PCS".equals(unit)) ||
            available == null || closing == null || knownLoss == null || price == null ||
            available.signum() < 0 || closing.signum() < 0 || knownLoss.signum() < 0 || price.signum() < 0 ||
            available.scale() > 3 || closing.scale() > 3 || knownLoss.scale() > 3 || price.scale() > 2 ||
            closing.add(knownLoss).compareTo(available) > 0) {
            throw new IllegalArgumentException("Invalid stock quantities: closing plus known losses cannot exceed available stock");
        }
        this.day = day;
        this.productName = productName.trim();
        this.productKey = productName.trim().toUpperCase(java.util.Locale.ROOT) + ":" + unit;
        this.unit = unit;
        this.availableQty = available.setScale(3);
        this.closingQty = closing.setScale(3);
        this.knownLossQty = knownLoss.setScale(3);
        this.sellingPrice = price.setScale(2);
    }
    public BigDecimal getImpliedSoldQty() { return availableQty.subtract(closingQty).subtract(knownLossQty); }
    public BigDecimal getEstimatedRetailValue() { return getImpliedSoldQty().multiply(sellingPrice).setScale(2,RoundingMode.HALF_UP); }
    public Long getId() { return id; }
    public HistoricalDay getDay() { return day; }
    public String getProductName() { return productName; }
    public String getProductKey() { return productKey; }
    public String getUnit() { return unit; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public BigDecimal getClosingQty() { return closingQty; }
    public BigDecimal getKnownLossQty() { return knownLossQty; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
}
