package com.smartstock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 24)
    private CustomerType customerType = CustomerType.RETAIL;

    protected Customer() { }

    public Customer(String name, String phone) {
        this(name, phone, CustomerType.RETAIL);
    }

    public Customer(String name, String phone, CustomerType type) {
        if (name == null || name.isBlank() || name.trim().length() > 255 ||
                phone == null || phone.isBlank() || phone.trim().length() > 255 || type == null) {
            throw new IllegalArgumentException("Customer name, contact and type are required");
        }
        this.name = name.trim();
        this.phone = phone.trim();
        this.customerType = type;
    }

    public void charge(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid charge");
        }
        balance = balance.add(amount);
    }

    public void pay(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2 ||
                amount.compareTo(balance) > 0) {
            throw new IllegalArgumentException(
                    "Payment must be positive and cannot exceed the outstanding balance");
        }
        balance = balance.subtract(amount);
    }

    public boolean isIndependentStall() {
        return customerType == CustomerType.INDEPENDENT_STALL;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public BigDecimal getBalance() { return balance; }
    public CustomerType getCustomerType() { return customerType; }
}
