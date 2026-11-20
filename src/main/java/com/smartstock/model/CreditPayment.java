package com.smartstock.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_credit_payment_request_id", columnNames = "request_id"))
public class CreditPayment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="request_id", length=36, unique=true) private String requestId;
 @ManyToOne(optional=false,fetch=FetchType.EAGER) private Customer customer;
 @Column(nullable=false,precision=14,scale=2) private BigDecimal amount;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private PaymentMethod method;
 @Column(nullable=false) private LocalDateTime paidAt;
 @Column(name="business_date") private LocalDate businessDate;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="cashier_id") private Employee cashier;
 public Employee getCashier(){return cashier;}
 public void assignCashier(Employee employee){if(employee==null || employee.getRole()!=EmployeeRole.CASHIER) throw new IllegalArgumentException("Cashier required");cashier=employee;}
 protected CreditPayment(){}
 public CreditPayment(Customer customer,BigDecimal amount,PaymentMethod method){this.customer=customer;this.amount=amount;this.method=method;this.paidAt=LocalDateTime.now();this.businessDate=LocalDate.now(ZoneId.of("Asia/Manila"));}
 public void assignRequestId(String value){if(value==null || !value.matches("[0-9a-fA-F-]{36}"))throw new IllegalArgumentException("Valid payment request ID required");requestId=value;}
 public void assignBusinessDate(LocalDate date){if(date==null||date.isAfter(LocalDate.now(ZoneId.of("Asia/Manila"))))throw new IllegalArgumentException("Valid non-future payment date required");businessDate=date;}
 public LocalDate getBusinessDate(){return businessDate!=null?businessDate:paidAt.toLocalDate();}
 public String getRequestId(){return requestId;}
 public Long getId(){return id;}public Customer getCustomer(){return customer;}public BigDecimal getAmount(){return amount;}public PaymentMethod getMethod(){return method;}public LocalDateTime getPaidAt(){return paidAt;}
}
