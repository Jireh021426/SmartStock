
package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "physical_stock_audit")
public class PhysicalStockAudit {

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Product product;

    @ManyToOne(optional = false)
    private StockLocation location;

    @ManyToOne(optional = false)
    private Employee auditor;

    @ManyToOne
    private Employee reviewer;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal expectedKg;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal countedKg;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal varianceKg;

    @Column(nullable = false, length = 1000)
    private String countNote;

    @Column(length = 1000)
    private String reviewNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime countedAt;

    private LocalDateTime reviewedAt;

    protected PhysicalStockAudit() {
    }

    public PhysicalStockAudit(
            Product product,
            StockLocation location,
            Employee auditor,
            BigDecimal expectedKg,
            BigDecimal countedKg,
            String countNote) {

        if (product == null || location == null || auditor == null
                || expectedKg == null || countedKg == null
                || expectedKg.signum() < 0 || countedKg.signum() < 0
                || expectedKg.scale() > 3 || countedKg.scale() > 3
                || countNote == null || countNote.isBlank()
                || countNote.trim().length() > 1000) {
            throw new IllegalArgumentException("Invalid physical audit information.");
        }

        this.product = product;
        this.location = location;
        this.auditor = auditor;
        this.expectedKg = expectedKg.setScale(3);
        this.countedKg = countedKg.setScale(3);
        this.varianceKg = this.countedKg.subtract(this.expectedKg);
        this.countNote = countNote.trim();
        this.countedAt = LocalDateTime.now();
    }

    public void review(Employee manager, boolean approve, String note) {
        if (status != Status.PENDING) {
            throw new IllegalStateException("Audit already reviewed.");
        }
        if (manager == null || manager.getId().equals(auditor.getId())) {
            throw new IllegalArgumentException("Independent manager review required.");
        }
        if (note == null || note.isBlank() || note.trim().length() > 1000) {
            throw new IllegalArgumentException("Review explanation is required.");
        }

        this.reviewer = manager;
        this.reviewNote = note.trim();
        this.status = approve ? Status.APPROVED : Status.REJECTED;
        this.reviewedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public StockLocation getLocation() { return location; }
    public Employee getAuditor() { return auditor; }
    public Employee getReviewer() { return reviewer; }
    public BigDecimal getExpectedKg() { return expectedKg; }
    public BigDecimal getCountedKg() { return countedKg; }
    public BigDecimal getVarianceKg() { return varianceKg; }
    public String getCountNote() { return countNote; }
    public String getReviewNote() { return reviewNote; }
    public Status getStatus() { return status; }
    public LocalDateTime getCountedAt() { return countedAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
