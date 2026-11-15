
package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "cashier_remittance",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_remittance_cashier_date",
        columnNames = {"cashier_id", "business_date"}
    )
)
public class Remittance {

    public enum Status {
        SUBMITTED, VERIFIED, FLAGGED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "cashier_id", nullable = false)
    private Employee cashier;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private StockLocation location;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedCash;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal expectedGcash;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal declaredCash;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal declaredGcash;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cashVariance;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal gcashVariance;

    // Nullable to preserve historical reviews.
    @Column(precision = 14, scale = 2)
    private BigDecimal confirmedCash;

    @Column(precision = 14, scale = 2)
    private BigDecimal confirmedGcash;

    @Column(precision = 14, scale = 2)
    private BigDecimal confirmedCashVariance;

    @Column(precision = 14, scale = 2)
    private BigDecimal confirmedGcashVariance;

    @Column(precision = 14, scale = 2)
    private BigDecimal cashDeclarationDifference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    private Employee verifiedBy;

    private LocalDateTime verifiedAt;

    @Column(length = 1000)
    private String reviewNote;

    protected Remittance() {}

    public Remittance(
            Employee cashier,
            StockLocation location,
            LocalDate date,
            BigDecimal expectedCash,
            BigDecimal expectedGcash,
            BigDecimal declaredCash,
            BigDecimal declaredGcash) {

        this.cashier = cashier;
        this.location = location;
        this.businessDate = date;
        this.expectedCash = expectedCash;
        this.expectedGcash = expectedGcash;
        this.declaredCash = declaredCash;
        this.declaredGcash = declaredGcash;

        this.cashVariance = declaredCash.subtract(expectedCash);
        this.gcashVariance = declaredGcash.subtract(expectedGcash);

        this.status = Status.SUBMITTED;
        this.submittedAt = LocalDateTime.now();
    }

    public void review(
            Employee reviewer,
            BigDecimal countedCash,
            BigDecimal confirmedGcash,
            String note) {

        if (status != Status.SUBMITTED) {
            throw new IllegalStateException(
                "Remittance already reviewed"
            );
        }

        if (reviewer == null ||
            (reviewer.getRole() != EmployeeRole.ADMIN &&
             reviewer.getRole() != EmployeeRole.MANAGER)) {
            throw new IllegalArgumentException(
                "Manager approval required"
            );
        }

        if (reviewer.getId().equals(cashier.getId())) {
            throw new IllegalArgumentException(
                "Cannot verify your own remittance"
            );
        }

        validateAmount(countedCash, "Counted cash");
        validateAmount(confirmedGcash, "Confirmed GCash");

        if (note != null && note.length() > 1000) {
            throw new IllegalArgumentException(
                "Review note too long"
            );
        }

        this.confirmedCash = countedCash;
        this.confirmedGcash = confirmedGcash;

        this.confirmedCashVariance =
            countedCash.subtract(expectedCash);

        this.confirmedGcashVariance =
            confirmedGcash.subtract(expectedGcash);

        this.cashDeclarationDifference =
            countedCash.subtract(declaredCash);

        this.verifiedBy = reviewer;
        this.verifiedAt = LocalDateTime.now();
        this.reviewNote = note == null ? "" : note.trim();

        boolean matchesExpected =
            confirmedCashVariance.signum() == 0 &&
            confirmedGcashVariance.signum() == 0;

        boolean matchesDeclaration =
            countedCash.compareTo(declaredCash) == 0 &&
            confirmedGcash.compareTo(declaredGcash) == 0;

        this.status = matchesExpected && matchesDeclaration
            ? Status.VERIFIED
            : Status.FLAGGED;
    }

    private void validateAmount(BigDecimal amount, String label) {
        if (amount == null ||
            amount.signum() < 0 ||
            amount.scale() > 2 ||
            amount.precision() - amount.scale() > 12) {
            throw new IllegalArgumentException(
                label + " must be nonnegative, with at most two decimals"
            );
        }
    }

    public Long getId() { return id; }
    public Employee getCashier() { return cashier; }
    public StockLocation getLocation() { return location; }
    public LocalDate getBusinessDate() { return businessDate; }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public BigDecimal getExpectedGcash() { return expectedGcash; }
    public BigDecimal getDeclaredCash() { return declaredCash; }
    public BigDecimal getDeclaredGcash() { return declaredGcash; }
    public BigDecimal getCashVariance() { return cashVariance; }
    public BigDecimal getGcashVariance() { return gcashVariance; }
    public BigDecimal getConfirmedCash() { return confirmedCash; }
    public BigDecimal getConfirmedGcash() { return confirmedGcash; }
    public BigDecimal getConfirmedCashVariance() {
        return confirmedCashVariance;
    }
    public BigDecimal getConfirmedGcashVariance() {
        return confirmedGcashVariance;
    }
    public BigDecimal getCashDeclarationDifference() {
        return cashDeclarationDifference;
    }
    public Status getStatus() { return status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public Employee getVerifiedBy() { return verifiedBy; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public String getReviewNote() { return reviewNote; }
}
