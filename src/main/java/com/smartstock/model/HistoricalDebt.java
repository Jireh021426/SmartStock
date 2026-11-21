package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "historical_debt", uniqueConstraints = {
    @UniqueConstraint(name = "uk_historical_debt_day_customer", columnNames = {"day_id", "customer_key"})
})
public class HistoricalDebt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "day_id", nullable = false)
    private HistoricalDay day;
    @Column(name = "customer_name", length = 100, nullable = false) private String customerName;
    @Column(name = "customer_key", length = 100, nullable = false) private String customerKey;
    @Column(name = "closing_balance", nullable = false, precision = 16, scale = 2)
    private BigDecimal closingBalance;
    protected HistoricalDebt() {}
    public HistoricalDebt(HistoricalDay day, String name, BigDecimal amount) {
        if (day == null || name == null || name.isBlank() || name.trim().length() > 100 ||
                amount == null || amount.signum() < 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Customer and valid outstanding debt are required");
        }
        this.day = day;
        this.customerName = name.trim();
        this.customerKey = name.trim().toUpperCase(java.util.Locale.ROOT);
        this.closingBalance = amount.setScale(2);
    }
    public Long getId() { return id; }
    public HistoricalDay getDay() { return day; }
    public String getCustomerName() { return customerName; }
    public String getCustomerKey() { return customerKey; }
    public BigDecimal getClosingBalance() { return closingBalance; }
}
