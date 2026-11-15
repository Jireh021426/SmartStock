package com.smartstock.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @DiscriminatorValue("WHOLE")
public class WholeChicken extends Product {
 protected WholeChicken(){}
 public WholeChicken(String sku,String name,BigDecimal price,BigDecimal threshold){super(sku,name,price,threshold);}
 @Override public String getCategory(){return "Whole Chicken";}
}
