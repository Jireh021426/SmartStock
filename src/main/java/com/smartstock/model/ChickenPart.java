package com.smartstock.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @DiscriminatorValue("PART")
public class ChickenPart extends Product {
 protected ChickenPart(){}
 public ChickenPart(String sku,String name,BigDecimal price,BigDecimal threshold){super(sku,name,price,threshold);}
 @Override public String getCategory(){return "Chicken Part";}
}
