package com.smartstock.model;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

public class DailyClosingForm {
    @NotNull(message = "Select a business date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate businessDate = LocalDate.now(ZoneId.of("Asia/Manila"));
    @NotNull(message = "Select a company stall")
    private Long locationId;
    @NotNull(message = "Select a product")
    private Long productId;
    @NotNull(message = "Enter the physically counted stock")
    @DecimalMin(value = "0.000", message = "Count must not be negative")
    @Digits(integer = 11, fraction = 3)
    private BigDecimal countedKg;
    @NotBlank(message = "Explain how the count was performed")
    @Size(max = 1000)
    private String note;

    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate businessDate) { this.businessDate = businessDate; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getCountedKg() { return countedKg; }
    public void setCountedKg(BigDecimal countedKg) { this.countedKg = countedKg; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
