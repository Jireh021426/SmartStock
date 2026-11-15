package com.smartstock.model;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public class ProductForm {
 @NotBlank private String sku;
 @NotBlank private String name;
 @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal price;
 @NotNull @DecimalMin("0.000") @Digits(integer=11,fraction=3) private BigDecimal lowStockKg=new BigDecimal("5.000");
 @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) private BigDecimal costPerKg=BigDecimal.ZERO;
 public BigDecimal getCostPerKg(){return costPerKg;}public void setCostPerKg(BigDecimal x){costPerKg=x;}
 @NotBlank private String type="PART";
 public String getSku(){return sku;}public void setSku(String x){sku=x;}
 public String getName(){return name;}public void setName(String x){name=x;}
 public BigDecimal getPrice(){return price;}public void setPrice(BigDecimal x){price=x;}
 public BigDecimal getLowStockKg(){return lowStockKg;}public void setLowStockKg(BigDecimal x){lowStockKg=x;}
 public String getType(){return type;}public void setType(String x){type=x;}
}
