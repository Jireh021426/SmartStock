
package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_transfer")
public class StockTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "source_location_id", nullable = false)
    private StockLocation sourceLocation;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "destination_location_id", nullable = false)
    private StockLocation destinationLocation;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private Employee sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id")
    private Employee receiver;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal dispatchedKg;

    @Column(precision = 14, scale = 3)
    private BigDecimal receivedKg;

    @Column(precision = 14, scale = 3)
    private BigDecimal discrepancyKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockTransferStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dispatchedAt;

    private LocalDateTime receivedAt;

    @Column(length = 255)
    private String dispatchNote;

    @Column(length = 255)
    private String receivingNote;

    protected StockTransfer() {
    }

    public StockTransfer(
            Product product,
            StockLocation sourceLocation,
            StockLocation destinationLocation,
            Employee sender,
            BigDecimal dispatchedKg,
            String dispatchNote) {

        if (product == null || sourceLocation == null ||
                destinationLocation == null || sender == null) {
            throw new IllegalArgumentException(
                    "Product, locations and sender are required.");
        }

        if (sourceLocation.getId().equals(
                destinationLocation.getId())) {
            throw new IllegalArgumentException(
                    "Source and destination must be different.");
        }

        validateWeight(dispatchedKg, false);

        this.product = product;
        this.sourceLocation = sourceLocation;
        this.destinationLocation = destinationLocation;
        this.sender = sender;
        this.dispatchedKg = dispatchedKg.setScale(3);
        this.dispatchNote = cleanNote(dispatchNote);
        this.status = StockTransferStatus.DISPATCHED;
        this.dispatchedAt = LocalDateTime.now();
    }

    public void confirmReceipt(
            Employee receiver,
            BigDecimal actualKg,
            String note) {

        if (status != StockTransferStatus.DISPATCHED) {
            throw new IllegalStateException(
                    "This transfer has already been confirmed.");
        }

        if (receiver == null || !receiver.isActive()) {
            throw new IllegalArgumentException(
                    "An active receiver is required.");
        }

        if (receiver.getId().equals(sender.getId())) {
            throw new IllegalArgumentException(
                    "Sender cannot confirm their own transfer.");
        }

        validateWeight(actualKg, true);

        if (actualKg.compareTo(dispatchedKg) > 0) {
            throw new IllegalArgumentException(
                    "Received weight cannot exceed dispatched weight. " +
                    "Record excess stock through a separate investigation.");
        }

        this.receiver = receiver;
        this.receivedKg = actualKg.setScale(3);
        this.discrepancyKg =
                dispatchedKg.subtract(this.receivedKg)
                        .setScale(3);

        this.receivingNote = cleanNote(note);
        this.receivedAt = LocalDateTime.now();

        this.status = discrepancyKg.signum() == 0
                ? StockTransferStatus.RECEIVED
                : StockTransferStatus.DISCREPANCY;
    }

    private static void validateWeight(
            BigDecimal weight,
            boolean allowZero) {

        if (weight == null ||
                weight.scale() > 3 ||
                (allowZero
                        ? weight.signum() < 0
                        : weight.signum() <= 0)) {
            throw new IllegalArgumentException(
                    "Weight must be valid with at most 3 decimal places.");
        }
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }

        String cleaned = note.trim();

        if (cleaned.length() > 255) {
            throw new IllegalArgumentException(
                    "Note must not exceed 255 characters.");
        }

        return cleaned;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public StockLocation getSourceLocation() {
        return sourceLocation;
    }

    public StockLocation getDestinationLocation() {
        return destinationLocation;
    }

    public Employee getSender() {
        return sender;
    }

    public Employee getReceiver() {
        return receiver;
    }

    public BigDecimal getDispatchedKg() {
        return dispatchedKg;
    }

    public BigDecimal getReceivedKg() {
        return receivedKg;
    }

    public BigDecimal getDiscrepancyKg() {
        return discrepancyKg;
    }

    public StockTransferStatus getStatus() {
        return status;
    }

    public LocalDateTime getDispatchedAt() {
        return dispatchedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public String getDispatchNote() {
        return dispatchNote;
    }

    public String getReceivingNote() {
        return receivingNote;
    }
}
