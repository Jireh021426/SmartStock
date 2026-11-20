package com.smartstock.model;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

public class IndependentPaymentForm {
    @NotNull
    private Long customerId;
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;
    @NotNull
    private PaymentMethod method = PaymentMethod.CASH;
    @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate businessDate = LocalDate.now(ZoneId.of("Asia/Manila"));
    @NotBlank
    private String requestId = java.util.UUID.randomUUID().toString();

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long x) { customerId = x; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal x) { amount = x; }
    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod x) { method = x; }
    public LocalDate getBusinessDate() { return businessDate; }
    public void setBusinessDate(LocalDate x) { businessDate = x; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String x) { requestId = x; }
}
