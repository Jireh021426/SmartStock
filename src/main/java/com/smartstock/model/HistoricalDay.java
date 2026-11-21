package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Historical source evidence only: never creates sales, debt or inventory postings. */
@Entity
@Table(name = "historical_day", uniqueConstraints = {
        @UniqueConstraint(name = "uk_historical_day_location_date", columnNames = {"location_id", "business_date"})
})
public class HistoricalDay {
    public enum Status { DRAFT, REVIEWED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id", nullable = false) private StockLocation location;
    @Column(name = "business_date", nullable = false) private LocalDate businessDate;
    @Column(name = "collections", precision = 16, scale = 2, nullable = false) private BigDecimal collections;
    @Column(name = "source_file", length = 255, nullable = false) private String sourceFile;
    @Column(name = "notes", length = 1000, nullable = false) private String notes;
    @Column(name = "debts_complete", nullable = false) private boolean debtsComplete;
    @Column(name = "stock_complete", nullable = false) private boolean stockComplete;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;
    @Column(name = "recorded_by", length = 100, nullable = false) private String recordedBy;
    @Column(name = "recorded_at", nullable = false, updatable = false) private LocalDateTime recordedAt;
    @Column(name = "last_edited_by", length = 100) private String lastEditedBy;
    @Column(name = "last_edited_at") private LocalDateTime lastEditedAt;
    @Column(name = "reviewed_by", length = 100) private String reviewedBy;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Column(name = "review_note", length = 1000) private String reviewNote;

    protected HistoricalDay() {}
    public HistoricalDay(StockLocation location, LocalDate date, BigDecimal collections,
                         String sourceFile, String notes, String recorder) {
        if (location == null || !location.isActive() || !"STALL".equalsIgnoreCase(location.getLocationType())) {
            throw new IllegalArgumentException("Choose an active company stall");
        }
        if (date == null || !date.isBefore(LocalDate.now(ZoneId.of("Asia/Manila")))) {
            throw new IllegalArgumentException("Historical day must be earlier than today");
        }
        if (collections == null || collections.signum() < 0 || collections.scale() > 2) {
            throw new IllegalArgumentException("Collections must be a nonnegative peso amount");
        }
        if (sourceFile == null || sourceFile.isBlank() || sourceFile.trim().length() > 255 ||
                notes == null || notes.trim().length() > 1000 ||
                recorder == null || recorder.isBlank() || recorder.length() > 100) {
            throw new IllegalArgumentException("Source reference and recorder are required");
        }
        this.location = location;
        this.businessDate = date;
        this.collections = collections.setScale(2);
        this.sourceFile = sourceFile.trim();
        this.notes = notes.trim();
        this.recordedBy = recorder;
        this.recordedAt = LocalDateTime.now();
    }
    public void requireDraft() {
        if (status != Status.DRAFT) throw new IllegalStateException("Reviewed historical records cannot be changed");
    }
    public void reviseSummary(BigDecimal newCollections, String newSourceFile, String newNotes, String editor) {
        requireDraft();
        if (newCollections == null || newCollections.signum() < 0 || newCollections.scale() > 2 ||
                newSourceFile == null || newSourceFile.isBlank() || newSourceFile.trim().length() > 255 ||
                newNotes == null || newNotes.trim().length() > 1000 ||
                editor == null || editor.isBlank() || editor.length() > 100) {
            throw new IllegalArgumentException("Enter valid collections, source file and notes");
        }
        collections = newCollections.setScale(2);
        sourceFile = newSourceFile.trim();
        notes = newNotes.trim();
        lastEditedAt = LocalDateTime.now();
        lastEditedBy = editor;
    }
    public void setDebtsComplete(boolean value) { requireDraft(); debtsComplete = value; }
    public void setStockComplete(boolean value) { requireDraft(); stockComplete = value; }
    public void review(String reviewer, String note) {
        requireDraft();
        if (reviewer == null || reviewer.isBlank() || reviewer.equalsIgnoreCase(recordedBy))
            throw new IllegalArgumentException("Independent manager review is required");
        if (!debtsComplete || !stockComplete)
            throw new IllegalStateException("Mark the debt and stock lists complete before review");
        if (note == null || note.isBlank() || note.trim().length() > 1000)
            throw new IllegalArgumentException("Review explanation is required");
        status = Status.REVIEWED;
        reviewedAt = LocalDateTime.now();
        reviewedBy = reviewer;
        reviewNote = note.trim();
    }
    public Long getId() { return id; }
    public StockLocation getLocation() { return location; }
    public LocalDate getBusinessDate() { return businessDate; }
    public BigDecimal getCollections() { return collections; }
    public String getSourceFile() { return sourceFile; }
    public String getNotes() { return notes; }
    public boolean isDebtsComplete() { return debtsComplete; }
    public boolean isStockComplete() { return stockComplete; }
    public Status getStatus() { return status; }
    public String getRecordedBy() { return recordedBy; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public String getLastEditedBy() { return lastEditedBy; }
    public LocalDateTime getLastEditedAt() { return lastEditedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewNote() { return reviewNote; }
}
