package com.smartstock.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class HistoricalDebtForm {
    @NotBlank @Size(max=100) private String customerName;
    @NotNull @DecimalMin("0.00") @Digits(integer=14, fraction=2) private BigDecimal closingBalance;
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String x) { customerName=x; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal x) { closingBalance=x; }
}
