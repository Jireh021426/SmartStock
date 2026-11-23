
package com.smartstock.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    // Authenticated person who actually entered the sale. Nullable for
    // legacy and independent-stall delivery records.
    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    public String getRecordedBy() { return recordedBy; }

    public void assignRecordedBy(String username) {
        if (recordedBy != null || username == null || username.isBlank()
                || username.trim().length() > 100) {
            throw new IllegalArgumentException("Valid sale recorder is required");
        }
        this.recordedBy = username.trim();
    }

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

    // An entry may be recorded after the actual delivery day.
    @Column(name = "business_date")
    private LocalDate businessDate;

    // Only populated for independent-stall credit deliveries.
    @Column(name = "delivery_reference", length = 80, unique = true)
    private String deliveryReference;

    // One hidden UUID per form submission prevents double-clicks and retries
    // from deducting stock or charging the customer more than once.
    @Column(name = "delivery_request_id", length = 36, unique = true)
    private String deliveryRequestId;

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
        this.businessDate = LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    // Independent stalls owe the full delivery value, including unsold stock.
    // Each delivery is a credit sale, not a stock transfer or a cashier sale.
    public static Sale independentDelivery(
            Product product,
            Customer customer,
            StockLocation source,
            BigDecimal weightKg,
            BigDecimal agreedPricePerKg,
            LocalDate date) {

        if (customer == null || !customer.isIndependentStall()) {
            throw new IllegalArgumentException("Select a registered independent stall");
        }
        if (source == null || !source.isActive() ||
                "LEGACY".equalsIgnoreCase(source.getCode()) ||
                !("STORAGE".equalsIgnoreCase(source.getLocationType()) ||
                  "STALL".equalsIgnoreCase(source.getLocationType()))) {
            throw new IllegalArgumentException("Select an active company stock location");
        }
        if (agreedPricePerKg == null || agreedPricePerKg.signum() <= 0 ||
                agreedPricePerKg.scale() > 2 ||
                date == null || date.isAfter(LocalDate.now(ZoneId.of("Asia/Manila")))) {
            throw new IllegalArgumentException("Valid price and delivery date are required");
        }

        Sale sale = new Sale(product, customer, source, weightKg, PaymentMethod.CREDIT);
        sale.pricePerKg = agreedPricePerKg.setScale(2);
        sale.total = sale.weightKg.multiply(sale.pricePerKg)
                .setScale(2, RoundingMode.HALF_UP);
        sale.grossProfit = sale.total.subtract(sale.costAtSale);
        sale.businessDate = date;
        // The unique reference is assigned after JPA generates the sale ID.
        return sale;
    }

    /**
     * Generate the reference only after the database has assigned an ID.
     * The sale ID guarantees uniqueness across dates, stalls and concurrent users.
     * Example: DR-20261116-IS005-000123
     */
    public void assignGeneratedDeliveryReference() {
        if (id == null || id <= 0 ||
                customer == null || !customer.isIndependentStall() ||
                customer.getId() == null || businessDate == null ||
                deliveryReference != null) {
            throw new IllegalStateException("Cannot assign a delivery reference to this sale");
        }
        this.deliveryReference = String.format(Locale.ROOT,
                "DR-%s-IS%03d-%06d",
                businessDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                customer.getId(), id);
    }

    public void assignDeliveryRequestId(String requestId) {
        if (deliveryRequestId != null || requestId == null) {
            throw new IllegalStateException("Delivery request ID cannot be reassigned");
        }
        try {
            deliveryRequestId = java.util.UUID.fromString(requestId).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid delivery request ID");
        }
    }

    public String getDeliveryRequestId() {
        return deliveryRequestId;
    }

    public boolean isIndependentDelivery() {
        return deliveryReference != null;
    }

    public LocalDate getBusinessDate() {
        return businessDate != null ? businessDate : soldAt.toLocalDate();
    }

    public String getDeliveryReference() {
        return deliveryReference;
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
