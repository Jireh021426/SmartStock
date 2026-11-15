
package com.smartstock.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class SaleForm {

    // ========================================
    // PRODUCT
    // ========================================

    @NotNull(message = "Please select a product")
    private Long productId;

    // ========================================
    // SELLING LOCATION
    // ========================================

    @NotNull(message = "Please select a selling location")
    private Long locationId;

    // ========================================
    // WEIGHT
    // ========================================

    @NotNull(message = "Please enter the weight")
    @DecimalMin(
            value = "0.001",
            message = "Weight must be greater than zero"
    )
    @Digits(
            integer = 11,
            fraction = 3,
            message = "Weight must have at most 3 decimal places"
    )
    private BigDecimal weightKg;

    // ========================================
    // PAYMENT METHOD
    // ========================================

    @NotNull(message = "Please select a payment method")
    private PaymentMethod paymentMethod =
            PaymentMethod.CASH;

    // ========================================
    // CUSTOMER (REQUIRED FOR UTANG)
    // ========================================

    private Long customerId;

    // ========================================
    // GETTERS AND SETTERS
    // ========================================

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

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(
            PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
}
