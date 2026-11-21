package com.smartstock.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class HistoricalStockForm {
    @NotBlank @Size(max=100) private String productName;
    @NotBlank private String unit = "KG";
    @NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) private BigDecimal availableQty;
    @NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) private BigDecimal closingQty;
    @NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) private BigDecimal knownLossQty = BigDecimal.ZERO;
    @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal sellingPrice;
    public String getProductName() {return productName;}
    public void setProductName(String x) {productName=x;}
    public String getUnit() {return unit;}
    public void setUnit(String x) {unit=x;}
    public BigDecimal getAvailableQty() {return availableQty;}
    public void setAvailableQty(BigDecimal x) {availableQty=x;}
    public BigDecimal getClosingQty() {return closingQty;}
    public void setClosingQty(BigDecimal x) {closingQty=x;}
    public BigDecimal getKnownLossQty() {return knownLossQty;}
    public void setKnownLossQty(BigDecimal x) {knownLossQty=x;}
    public BigDecimal getSellingPrice() {return sellingPrice;}
    public void setSellingPrice(BigDecimal x) {sellingPrice=x;}
}
