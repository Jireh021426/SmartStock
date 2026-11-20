package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class IndependentStallService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Manila");

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository locationInventories;
    private final SaleRepository sales;
    private final CreditPaymentRepository payments;
    private final StockMovementRepository movements;

    public IndependentStallService(
            CustomerRepository customers,
            ProductRepository products,
            StockLocationRepository locations,
            LocationInventoryRepository locationInventories,
            SaleRepository sales,
            CreditPaymentRepository payments,
            StockMovementRepository movements) {
        this.customers = customers;
        this.products = products;
        this.locations = locations;
        this.locationInventories = locationInventories;
        this.sales = sales;
        this.payments = payments;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<Customer> stalls() {
        return customers.findByCustomerTypeOrderByNameAsc(CustomerType.INDEPENDENT_STALL);
    }

    @Transactional(readOnly = true)
    public List<Sale> deliveries() {
        return sales.findByDeliveryReferenceIsNotNullOrderBySoldAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<CreditPayment> collections() {
        return payments.findByCustomerCustomerTypeAndCashierIsNullOrderByPaidAtDescIdDesc(
                CustomerType.INDEPENDENT_STALL);
    }

    @Transactional(readOnly = true)
    public BigDecimal outstanding() {
        return stalls().stream().map(Customer::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void register(IndependentStallForm form) {
        if (form == null || form.getName() == null || form.getName().isBlank() ||
                form.getPhone() == null || form.getPhone().isBlank()) {
            throw new IllegalArgumentException("Stall name and contact are required");
        }
        String phone = form.getPhone().trim();
        if (customers.existsByPhone(phone)) {
            throw new IllegalArgumentException("Contact / account identifier already exists");
        }
        customers.save(new Customer(form.getName(), phone, CustomerType.INDEPENDENT_STALL));
    }

    @Transactional
    public Sale recordDelivery(IndependentDeliveryForm form) {
        if (form == null || form.getCustomerId() == null ||
                form.getProductId() == null || form.getLocationId() == null ||
                form.getWeightKg() == null || form.getWeightKg().signum() <= 0 ||
                form.getWeightKg().scale() > 3 || form.getPricePerKg() == null ||
                form.getPricePerKg().signum() <= 0 || form.getPricePerKg().scale() > 2 ||
                form.getBusinessDate() == null ||
                form.getBusinessDate().isAfter(LocalDate.now(BUSINESS_ZONE)) ||
                form.getDeliveryReference() == null || form.getDeliveryReference().isBlank() ||
                form.getDeliveryReference().trim().length() > 80) {
            throw new IllegalArgumentException("Complete the delivery date, stall, product, source, quantity, price and reference");
        }
        String reference = form.getDeliveryReference().trim().toUpperCase(java.util.Locale.ROOT);
        if (sales.existsByDeliveryReference(reference)) {
            throw new IllegalArgumentException("Delivery reference already recorded; check delivery history");
        }

        // Keep the lock order the same as stock-in and cashier sales: product first.
        Product product = products.findLockedById(form.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        StockLocation location = locations.findById(form.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Company source location not found"));
        if (!location.isActive() || "LEGACY".equalsIgnoreCase(location.getCode()) ||
                !("STORAGE".equalsIgnoreCase(location.getLocationType()) ||
                  "STALL".equalsIgnoreCase(location.getLocationType()))) {
            throw new IllegalArgumentException("Select an active company storage or stall location");
        }

        LocationInventory balance = locationInventories
                .findLockedByLocationAndProduct(location.getId(), product.getId())
                .orElseThrow(() -> new IllegalArgumentException("No stock at the selected source location"));
        BigDecimal weight = form.getWeightKg().setScale(3);
        if (balance.getStockKg().compareTo(weight) < 0 ||
                product.getStockKg().compareTo(weight) < 0) {
            throw new IllegalArgumentException("Insufficient company stock for this delivery");
        }

        Customer stall = customers.findLockedById(form.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Independent stall not found"));
        if (!stall.isIndependentStall()) {
            throw new IllegalArgumentException("Selected account is not an independent stall");
        }

        // All of these operations commit or roll back together.
        Sale sale = sales.saveAndFlush(Sale.independentDelivery(
                product, stall, location, weight, form.getPricePerKg(),
                form.getBusinessDate(), reference));
        balance.changeStock(weight.negate());
        product.changeStock(weight.negate());
        stall.charge(sale.getTotal());

        StockMovement movement = new StockMovement(product, MovementType.STOCK_OUT,
                weight, "SALE #" + sale.getId() + " (INDEPENDENT STALL)");
        movement.assignLocation(location);
        movement.assignCategory(MovementCategory.SALE);
        movement.assignBusinessDate(form.getBusinessDate());
        movements.save(movement);
        return sale;
    }

    @Transactional
    public void recordPayment(IndependentPaymentForm form) {
        if (form == null || form.getCustomerId() == null || form.getAmount() == null ||
                form.getAmount().signum() <= 0 || form.getAmount().scale() > 2 ||
                form.getBusinessDate() == null ||
                form.getBusinessDate().isAfter(LocalDate.now(BUSINESS_ZONE)) ||
                form.getMethod() == null || form.getMethod() == PaymentMethod.CREDIT) {
            throw new IllegalArgumentException("Select a stall, Cash/GCash, date and positive payment");
        }
        String requestId;
        try {
            requestId = UUID.fromString(form.getRequestId()).toString();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Valid payment request ID required");
        }
        // Serialize changes to each customer balance before checking request-id reuse.
        Customer stall = customers.findLockedById(form.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Independent stall not found"));
        if (!stall.isIndependentStall()) {
            throw new IllegalArgumentException("Selected account is not an independent stall");
        }
        if (payments.findByRequestId(requestId).isPresent()) {
            throw new IllegalArgumentException("Payment was already submitted; check payment history");
        }
        stall.pay(form.getAmount());
        CreditPayment payment = new CreditPayment(stall, form.getAmount(), form.getMethod());
        // A null cashier distinguishes independent collections from cashiers' remittances.
        payment.assignRequestId(requestId);
        payment.assignBusinessDate(form.getBusinessDate());
        payments.saveAndFlush(payment);
    }
}
