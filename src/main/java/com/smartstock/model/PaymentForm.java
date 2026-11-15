package com.smartstock.model;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class PaymentForm {
 @NotNull private Long customerId;
 @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) private BigDecimal amount;
 @NotNull private PaymentMethod method=PaymentMethod.CASH;
 @NotBlank private String requestId;
 public String getRequestId(){return requestId;} public void setRequestId(String x){requestId=x;}
 public Long getCustomerId(){return customerId;}public void setCustomerId(Long x){customerId=x;}
 public BigDecimal getAmount(){return amount;}public void setAmount(BigDecimal x){amount=x;}
 public PaymentMethod getMethod(){return method;}public void setMethod(PaymentMethod x){method=x;}
}
