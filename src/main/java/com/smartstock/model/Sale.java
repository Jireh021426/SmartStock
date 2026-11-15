
package com.smartstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========================================
    // PRODUCT AND CUSTOMER
    // ========================================

    @ManyToOne(
            optional = false,
            fetch = FetchType.EAGER
    )
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER)
    private Customer customer;

    // ========================================
    // SELLING LOCATION
    // ========================================

    // Nullable because historical sales did not
    // record their originating stall.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private StockLocation location;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cashier_id")
    private Employee cashier; // null for pre-Phase-5 historical sales

    public Employee getCashier() { return cashier; }
    public void assignCashier(Employee employee) {
        if (employee == null || employee.getRole() != EmployeeRole.CASHIER)
            throw new IllegalArgumentException("Valid cashier required");
        this.cashier = employee;
    }

    // ========================================
    // SALE SNAPSHOTS
    // ========================================

    @Column(nullable = false)
    private String productName;

    @Column(
            nullable = false,
            precision = 14,
            scale = 3
    )
    private BigDecimal weightKg;

    @Column(
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal pricePerKg;

    @Column(
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal total;

    @Column(precision = 14, scale = 2)
    private BigDecimal costAtSale = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal grossProfit = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false)
    private LocalDateTime soldAt;

    // Required by JPA
    protected Sale() {
    }

    // ========================================
    // ORIGINAL CONSTRUCTOR
    // ========================================

    // Retained for compatibility with existing code.
    // New transactions should use the constructor
    // that includes StockLocation.
    public Sale(
            Product product,
            Customer customer,
            BigDecimal weight,
            PaymentMethod method) {

        initializeSale(
                product,
                customer,
                null,
                weight,
                method
        );
    }

    // ========================================
    // LOCATION-AWARE CONSTRUCTOR
    // ========================================

    public Sale(
            Product product,
            Customer customer,
            StockLocation location,
            BigDecimal weight,
            PaymentMethod method) {

        if (location == null || !location.isActive()) {
            throw new IllegalArgumentException(
                    "A valid active selling location is required");
        }

        initializeSale(
                product,
                customer,
                location,
                weight,
                method
        );
    }

    // ========================================
    // INITIALIZE SALE
    // ========================================

    private void initializeSale(
            Product product,
            Customer customer,
            StockLocation location,
            BigDecimal weight,
            PaymentMethod method) {

        if (product == null ||
                weight == null ||
                weight.signum() <= 0 ||
                weight.scale() > 3 ||
                method == null) {

            throw new IllegalArgumentException(
                    "Invalid sale information");
        }

        if (product.getPrice() == null ||
                product.getPrice().signum() <= 0) {

            throw new IllegalArgumentException(
                    "A valid selling price is required");
        }

        if (product.getCostPerKg() == null ||
                product.getCostPerKg().signum() <= 0) {

            throw new IllegalArgumentException(
                    "A valid purchase cost is required");
        }

        if (method == PaymentMethod.CREDIT &&
                customer == null) {

            throw new IllegalArgumentException(
                    "A customer is required for credit sales");
        }

        this.product = product;
        this.customer = customer;
        this.location = location;

        // Snapshot product name and selling price.
        this.productName = product.getName();

        this.weightKg = weight.setScale(3);

        this.pricePerKg = product.getPrice()
                .setScale(2, RoundingMode.HALF_UP);

        // Revenue
        this.total = this.weightKg
                .multiply(this.pricePerKg)
                .setScale(2, RoundingMode.HALF_UP);

        // Cost of goods sold
        this.costAtSale = this.weightKg
                .multiply(product.getCostPerKg())
                .setScale(2, RoundingMode.HALF_UP);

        // Gross profit
        this.grossProfit =
                this.total.subtract(this.costAtSale);

        this.paymentMethod = method;
        this.soldAt = LocalDateTime.now();
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

    public Customer getCustomer() {
        return customer;
    }

    public StockLocation getLocation() {
        return location;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getPricePerKg() {
        return pricePerKg;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getCostAtSale() {
        return costAtSale == null
                ? BigDecimal.ZERO
                : costAtSale;
    }

    public BigDecimal getGrossProfit() {
        return grossProfit == null
                ? BigDecimal.ZERO
                : grossProfit;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime getSoldAt() {
        return soldAt;
    }
}
