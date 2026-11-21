package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only stock count and sales reconciliation. A closing does not create a
 * sale, adjust stock or generate a new stock-in on the following day.
 */
@Entity
@Table(name = "daily_stock_closing", indexes = {
        @Index(name = "idx_daily_closing_date_location", columnList = "business_date,location_id")
})
public class DailyStockClosing {
    public enum Status { PENDING, APPROVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StockLocation location;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "book_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal bookKg;

    @Column(name = "counted_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal countedKg;

    @Column(name = "recorded_sales_kg", nullable = false, precision = 14, scale = 3)
    private BigDecimal recordedSalesKg;

    @Column(name = "recorded_revenue", nullable = false, precision = 16, scale = 2)
    private BigDecimal recordedRevenue;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "counted_by", nullable = false, length = 100)
    private String countedBy;

    @Column(name = "counted_at", nullable = false, updatable = false)
    private LocalDateTime countedAt;

    @Column(name = "count_note", nullable = false, length = 1000)
    private String countNote;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    protected DailyStockClosing() { }

    public DailyStockClosing(StockLocation location, Product product,
                             LocalDate businessDate, BigDecimal bookKg,
                             BigDecimal countedKg, BigDecimal recordedSalesKg,
                             BigDecimal recordedRevenue, BigDecimal unitPriceSnapshot,
                             String countedBy, String countNote) {
        if (location == null || product == null || businessDate == null ||
                bookKg == null || countedKg == null || recordedSalesKg == null ||
                recordedRevenue == null || unitPriceSnapshot == null ||
                bookKg.signum() < 0 || countedKg.signum() < 0 ||
                recordedSalesKg.signum() < 0 || recordedRevenue.signum() < 0 ||
                unitPriceSnapshot.signum() < 0 ||
                bookKg.scale() > 3 || countedKg.scale() > 3 ||
                recordedSalesKg.scale() > 3 || recordedRevenue.scale() > 2 ||
                unitPriceSnapshot.scale() > 2 ||
                countedBy == null || countedBy.isBlank() || countedBy.length() > 100 ||
                countNote == null || countNote.isBlank() || countNote.trim().length() > 1000) {
            throw new IllegalArgumentException("Invalid closing stock count");
        }
        this.location = location;
        this.product = product;
        this.businessDate = businessDate;
        this.bookKg = bookKg.setScale(3);
        this.countedKg = countedKg.setScale(3);
        this.recordedSalesKg = recordedSalesKg.setScale(3);
        this.recordedRevenue = recordedRevenue.setScale(2);
        this.unitPriceSnapshot = unitPriceSnapshot.setScale(2);
        this.countedBy = countedBy;
        this.countNote = countNote.trim();
        this.countedAt = LocalDateTime.now();
    }

    public void review(String reviewer, boolean approve, String note) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Closing count has already been reviewed");
        }
        if (reviewer == null || reviewer.isBlank() ||
                reviewer.equalsIgnoreCase(countedBy)) {
            throw new IllegalArgumentException("An independent reviewer is required");
        }
        if (note == null || note.isBlank() || note.trim().length() > 1000) {
            throw new IllegalArgumentException("Review explanation is required");
        }
        if (approve && getVarianceKg().signum() != 0) {
            throw new IllegalStateException(
                    "Count differs from recorded stock. Record any missing sales, " +
                    "or investigate with Physical Audits, then recount. " +
                    "An unexplained shortage is not automatically a sale.");
        }
        reviewedBy = reviewer;
        reviewedAt = LocalDateTime.now();
        reviewNote = note.trim();
        status = approve ? Status.APPROVED : Status.REJECTED;
    }

    public Long getId() { return id; }
    public StockLocation getLocation() { return location; }
    public Product getProduct() { return product; }
    public LocalDate getBusinessDate() { return businessDate; }
    public BigDecimal getBookKg() { return bookKg; }
    public BigDecimal getCountedKg() { return countedKg; }
    public BigDecimal getRecordedSalesKg() { return recordedSalesKg; }
    public BigDecimal getRecordedRevenue() { return recordedRevenue; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    /** Positive value means less physical stock than the book balance. */
    public BigDecimal getVarianceKg() { return bookKg.subtract(countedKg).setScale(3); }
    public BigDecimal getPotentialUnrecordedKg() { return getVarianceKg().max(BigDecimal.ZERO.setScale(3)); }
    public BigDecimal getPotentialUnrecordedRevenue() {
        return getPotentialUnrecordedKg().multiply(unitPriceSnapshot)
                .setScale(2, RoundingMode.HALF_UP);
    }
    public BigDecimal getPotentialTotalSoldKg() {
        return recordedSalesKg.add(getPotentialUnrecordedKg());
    }
    public Status getStatus() { return status; }
    public String getCountedBy() { return countedBy; }
    public LocalDateTime getCountedAt() { return countedAt; }
    public String getCountNote() { return countNote; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
}
