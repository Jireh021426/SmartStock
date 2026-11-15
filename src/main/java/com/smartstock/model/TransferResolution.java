
package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "transfer_resolution",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_transfer_resolution_transfer",
        columnNames = "transfer_id"
    )
)
public class TransferResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "resolved_by_id", nullable = false)
    private Employee resolvedBy;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal recoveredKg;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal confirmedLossKg;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal lossValue;

    @Column(nullable = false, length = 1000)
    private String explanation;

    @Column(nullable = false, updatable = false)
    private LocalDateTime resolvedAt;

    protected TransferResolution() {
    }

    public TransferResolution(
            StockTransfer transfer,
            Employee resolvedBy,
            BigDecimal recoveredKg,
            BigDecimal confirmedLossKg,
            BigDecimal lossValue,
            String explanation) {

        if (transfer == null || resolvedBy == null ||
                recoveredKg == null || confirmedLossKg == null ||
                lossValue == null || explanation == null ||
                explanation.isBlank() ||
                explanation.trim().length() > 1000) {
            throw new IllegalArgumentException(
                "Complete resolution information is required.");
        }

        if (recoveredKg.signum() < 0 ||
                confirmedLossKg.signum() < 0 ||
                lossValue.signum() < 0 ||
                recoveredKg.scale() > 3 ||
                confirmedLossKg.scale() > 3) {
            throw new IllegalArgumentException(
                "Invalid recovery or loss quantities.");
        }

        if (transfer.getStatus() !=
                StockTransferStatus.DISCREPANCY) {
            throw new IllegalArgumentException(
                "Only discrepant transfers can be resolved.");
        }

        if (recoveredKg.add(confirmedLossKg)
                .compareTo(transfer.getDiscrepancyKg()) != 0) {
            throw new IllegalArgumentException(
                "Recovered quantity plus confirmed loss must equal the discrepancy.");
        }

        this.transfer = transfer;
        this.resolvedBy = resolvedBy;
        this.recoveredKg = recoveredKg.setScale(3);
        this.confirmedLossKg = confirmedLossKg.setScale(3);
        this.lossValue = lossValue.setScale(2);
        this.explanation = explanation.trim();
        this.resolvedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public StockTransfer getTransfer() {
        return transfer;
    }

    public Employee getResolvedBy() {
        return resolvedBy;
    }

    public BigDecimal getRecoveredKg() {
        return recoveredKg;
    }

    public BigDecimal getConfirmedLossKg() {
        return confirmedLossKg;
    }

    public BigDecimal getLossValue() {
        return lossValue;
    }

    public String getExplanation() {
        return explanation;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public String getStatus() {
        return "RESOLVED";
    }
}
