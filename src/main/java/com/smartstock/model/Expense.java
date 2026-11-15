package com.smartstock.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity public class Expense {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String category;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal amount;
 @Column(nullable=false) private String description;
 @Column(nullable=false) private LocalDateTime incurredAt;
 protected Expense(){}
 public Expense(String category,BigDecimal amount,String description){this.category=category;this.amount=amount;this.description=description;this.incurredAt=LocalDateTime.now();}
 public Long getId(){return id;}public String getCategory(){return category;}public BigDecimal getAmount(){return amount;}public String getDescription(){return description;}public LocalDateTime getIncurredAt(){return incurredAt;}
}