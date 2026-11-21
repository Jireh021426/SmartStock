package com.smartstock.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class HistoricalSummaryEditForm {
    @NotNull @DecimalMin("0.00") @Digits(integer=14, fraction=2) private BigDecimal collections;
    @NotBlank @Size(max=255) private String sourceFile;
    @Size(max=1000) private String notes = "";
    public BigDecimal getCollections() { return collections; }
    public void setCollections(BigDecimal x) { collections=x; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String x) { sourceFile=x; }
    public String getNotes() { return notes; }
    public void setNotes(String x) { notes=x; }
}
