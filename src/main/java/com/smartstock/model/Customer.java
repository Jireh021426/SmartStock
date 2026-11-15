package com.smartstock.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity
public class Customer {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String name;
 @Column(nullable=false,unique=true) private String phone;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal balance=BigDecimal.ZERO.setScale(2);
 protected Customer(){}
 public Customer(String name,String phone){if(name==null||name.isBlank()||phone==null||phone.isBlank())throw new IllegalArgumentException("Customer name and contact are required");this.name=name.trim();this.phone=phone.trim();}
 public void charge(BigDecimal amount){if(amount==null||amount.signum()<=0)throw new IllegalArgumentException("Invalid charge");balance=balance.add(amount);}
 public void pay(BigDecimal amount){if(amount==null||amount.signum()<=0||amount.compareTo(balance)>0)throw new IllegalArgumentException("Payment must be positive and cannot exceed the outstanding balance");balance=balance.subtract(amount);}
 public Long getId(){return id;} public String getName(){return name;}public String getPhone(){return phone;}public BigDecimal getBalance(){return balance;}
}
