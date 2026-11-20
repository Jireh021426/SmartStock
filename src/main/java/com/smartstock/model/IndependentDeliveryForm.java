package com.smartstock.model;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

public class IndependentDeliveryForm {
    @NotNull(message = "Select an independent stall")
    private Long customerId;
    @NotNull(message = "Select a product")
    private Long productId;
    @NotNull(message = "Select the company source location")
    private Long locationId;
    @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3)
    private BigDecimal weightKg;
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
    private BigDecimal pricePerKg;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate businessDate = LocalDate.now(ZoneId.of("Asia/Manila"));
    @NotBlank(message = "Submission ID is required")
    private String requestId = java.util.UUID.randomUUID().toString();

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long x) { customerId = x; }
    public Long getProductId() { return productId; }
    public void setProductId(Long x) { productId = x; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long x) { locationId = x; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal x) { weightKg = x; }
    public BigDecimal getPricePerKg() { return pricePerKg; }
    public void setPricePerKg(BigDecimal x) { pricePerKg = x; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate x) { businessDate = x; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String x) { requestId = x; }
}
