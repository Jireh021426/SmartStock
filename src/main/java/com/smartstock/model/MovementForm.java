
package com.smartstock.model;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

public class MovementForm {

    @NotNull(message = "Please select a product")
    private Long productId;

    @NotNull(message = "Please select a stock location")
    private Long locationId;

    @NotNull(message = "Please select a movement type")
    private MovementType type = MovementType.STOCK_IN;

    @NotNull(message = "Please select a business date")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate businessDate =
            LocalDate.now(ZoneId.of("Asia/Manila"));

    @NotNull(message = "Please enter the weight")
    @DecimalMin(
            value = "0.001",
            message = "Weight must be positive"
    )
    @Digits(integer = 11, fraction = 3)
    private BigDecimal weightKg;

    @DecimalMin(
            value = "0.01",
            message = "Purchase cost must be greater than zero"
    )
    @Digits(integer = 10, fraction = 2)
    private BigDecimal purchaseCostPerKg;

    // Required for new supplier deliveries; not applicable to STOCK_OUT.
    private Long supplierId;

    @Size(max = 80, message = "Delivery receipt must be at most 80 characters")
    private String deliveryReceiptNumber;

    // Required for manual STOCK_OUT.
    // STOCK_IN is automatically classified as PURCHASE.
    private MovementCategory category;

    @NotBlank(message = "Please enter a reason")
    @Size(
            max = 255,
            message = "Reason must not exceed 255 characters"
    )
    private String reason;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getDeliveryReceiptNumber() {
        return deliveryReceiptNumber;
    }

    public void setDeliveryReceiptNumber(String deliveryReceiptNumber) {
        this.deliveryReceiptNumber = deliveryReceiptNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public MovementType getType() {
        return type;
    }

    public void setType(MovementType type) {
        this.type = type;
    }

    public LocalDate getBusinessDate() {
        return businessDate;
    }

    public void setBusinessDate(LocalDate businessDate) {
        this.businessDate = businessDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getPurchaseCostPerKg() {
        return purchaseCostPerKg;
    }

    public void setPurchaseCostPerKg(
            BigDecimal purchaseCostPerKg) {
        this.purchaseCostPerKg = purchaseCostPerKg;
    }

    public MovementCategory getCategory() {
        return category;
    }

    public void setCategory(MovementCategory category) {
        this.category = category;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
