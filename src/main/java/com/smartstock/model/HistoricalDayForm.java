package com.smartstock.model;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

public class HistoricalDayForm {
    @NotNull private Long locationId;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate businessDate;
    @NotNull @DecimalMin("0.00") @Digits(integer = 14, fraction = 2) private BigDecimal collections;
    @NotBlank @Size(max = 255) private String sourceFile;
    @Size(max = 1000) private String notes = "";
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long id) { locationId=id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate date) { businessDate=date; }
    public BigDecimal getCollections() { return collections; }
    public void setCollections(BigDecimal x) { collections=x; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String s) { sourceFile=s; }
    public String getNotes() { return notes; }
    public void setNotes(String s) { notes=s; }
}
