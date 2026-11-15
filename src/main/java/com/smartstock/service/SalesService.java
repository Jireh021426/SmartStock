
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class SalesService {

    private final EmployeeRepository employees;
    private final RemittanceRepository remittances;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SaleRepository sales;
    private final CreditPaymentRepository payments;
    private final StockMovementRepository movements;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository locationInventories;

    public SalesService(
            ProductRepository products,
            EmployeeRepository employees,
            RemittanceRepository remittances,
            CustomerRepository customers,
            SaleRepository sales,
            CreditPaymentRepository payments,
            StockMovementRepository movements,
            StockLocationRepository locations,
            LocationInventoryRepository locationInventories) {

        this.products = products;
        this.employees = employees;
        this.remittances = remittances;
        this.customers = customers;
        this.sales = sales;
        this.payments = payments;
        this.movements = movements;
        this.locations = locations;
        this.locationInventories = locationInventories;
    }

    // ========================================
    // SALES AND CUSTOMER LISTS
    // ========================================

    @Transactional(readOnly = true)
    public List<Sale> allSales() {
        return sales.findAllByOrderBySoldAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<Customer> allCustomers() {
        return customers.findAll();
    }

    @Transactional(readOnly = true)
    public List<CreditPayment> allPayments() {
        return payments.findAllByOrderByPaidAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public Sale sale(Long id) {
        return sales.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Sale not found"));
    }

    // ========================================
    // RECORD NEW SALE
    // ========================================

    @Transactional
    public Sale recordSale(String username, SaleForm form) {

        Employee cashier = requireCashier(username);
        requireOpenRemittanceDay(cashier);

        StockLocation assignedLocation = cashier.getAssignedLocation();

        // Validate form.
        if (form == null ||
                form.getProductId() == null ||
                form.getLocationId() == null ||
                form.getPaymentMethod() == null ||
                form.getWeightKg() == null ||
                form.getWeightKg().signum() <= 0 ||
                form.getWeightKg().scale() > 3) {

            throw new IllegalArgumentException(
                    "Choose a product, selling location, " +
                    "payment method and positive weight " +
                    "(up to 3 decimal places)");
        }

        BigDecimal weight = form.getWeightKg().setScale(3);

        // Lock product before changing inventory.
        Product product = products
                .findLockedById(form.getProductId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Product not found"));

        // Validate selling location.
        StockLocation location = locations
                .findById(form.getLocationId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Stock location not found"));

        if (!location.getId().equals(assignedLocation.getId())) {
            throw new IllegalArgumentException(
                    "You can sell only from your assigned stall: " +
                    assignedLocation.getName());
        }

        if ("LEGACY".equalsIgnoreCase(location.getCode()) ||
                !"STALL".equalsIgnoreCase(location.getLocationType())) {

            throw new IllegalArgumentException(
                    "Sales are permitted only from a physical STALL location.");
        }

        if (!location.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot sell from an inactive location");
        }

        // Lock location inventory.
        LocationInventory balance = locationInventories
                .findLockedByLocationAndProduct(
                        location.getId(),
                        product.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No inventory exists for " +
                                product.getName() +
                                " at " + location.getName()));

        // Check available stock.
        if (balance.getStockKg().compareTo(weight) < 0) {
            throw new IllegalArgumentException(
                    "Insufficient stock at " +
                    location.getName() +
                    ". Available: " +
                    balance.getStockKg() + " kg");
        }

        if (product.getStockKg().compareTo(weight) < 0) {
            throw new IllegalStateException(
                    "Company inventory is inconsistent " +
                    "with the requested sale");
        }

        // Validate selling price.
        if (product.getPrice() == null ||
                product.getPrice().signum() <= 0) {

            throw new IllegalArgumentException(
                    "Set a positive selling price " +
                    "before recording a sale");
        }

        // Validate purchase cost.
        if (product.getCostPerKg() == null ||
                product.getCostPerKg().signum() <= 0) {

            throw new IllegalArgumentException(
                    "Set a positive purchase cost " +
                    "per kg before recording a sale");
        }

        // Customer / utang validation.
        Customer customer = null;

        if (form.getPaymentMethod() == PaymentMethod.CREDIT) {

            if (form.getCustomerId() == null) {
                throw new IllegalArgumentException(
                        "Select a customer for utang sales");
            }

            customer = customers
                    .findLockedById(form.getCustomerId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Customer not found"));
        }

        // Deduct inventory from stall and company totals.
        balance.changeStock(weight.negate());
        product.changeStock(weight.negate());

        // Save sale.
        Sale sale = sales.save(
                new Sale(
                        product,
                        customer,
                        location,
                        weight,
                        form.getPaymentMethod()
                )
        );

        sale.assignCashier(cashier);

        // Update customer balance for credit sales.
        if (customer != null) {
            customer.charge(sale.getTotal());
        }

        // Record stock movement.
        StockMovement movement = new StockMovement(
                product,
                MovementType.STOCK_OUT,
                weight,
                "SALE #" + sale.getId() +
                        " (" + form.getPaymentMethod() + ")"
        );

        movement.assignLocation(location);
        movement.assignCategory(MovementCategory.SALE);

        movements.save(movement);

        return sale;
    }

    // ========================================
    // REMITTANCE DAY VALIDATION
    // ========================================

    private void requireOpenRemittanceDay(Employee cashier) {

        if (remittances.findByCashierIdAndBusinessDate(
                cashier.getId(),
                LocalDate.now()).isPresent()) {

            throw new IllegalStateException(
                    "Today's remittance is already submitted. " +
                    "Further transactions require a new business day " +
                    "or manager investigation.");
        }
    }

    // ========================================
    // CASHIER VALIDATION WITH DATABASE LOCK
    // ========================================

    private Employee requireCashier(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Cashier login required.");
        }

        // PESSIMISTIC_WRITE lock is held until the
        // enclosing transaction commits or rolls back.
        Employee cashier = employees
                .findLockedByUsername(
                        username.toLowerCase(Locale.ROOT))
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "A database cashier account is required."));

        if (!cashier.isActive() ||
                cashier.getRole() != EmployeeRole.CASHIER) {

            throw new IllegalArgumentException(
                    "Only active cashiers may record sales.");
        }

        StockLocation assigned = cashier.getAssignedLocation();

        if (assigned == null ||
                !assigned.isActive() ||
                "LEGACY".equalsIgnoreCase(assigned.getCode()) ||
                !"STALL".equalsIgnoreCase(assigned.getLocationType())) {

            throw new IllegalArgumentException(
                    "Cashier requires an active STALL assignment.");
        }

        return cashier;
    }

    // ========================================
    // CUSTOMER MANAGEMENT
    // ========================================

    @Transactional
    public void createCustomer(CustomerForm form) {

        if (form == null ||
                form.getName() == null ||
                form.getName().isBlank() ||
                form.getPhone() == null ||
                form.getPhone().isBlank()) {

            throw new IllegalArgumentException(
                    "Name and contact are required");
        }

        String phone = form.getPhone().trim();

        if (customers.existsByPhone(phone)) {
            throw new IllegalArgumentException(
                    "Contact already exists");
        }

        customers.save(
                new Customer(form.getName(), phone)
        );
    }

    // ========================================
    // RECORD UTANG PAYMENT
    // ========================================

    @Transactional
    public void recordPayment(String username, PaymentForm form) {

        Employee cashier = requireCashier(username);
        requireOpenRemittanceDay(cashier);

        if (form == null ||
                form.getCustomerId() == null ||
                form.getAmount() == null ||
                form.getAmount().signum() <= 0 ||
                form.getAmount().scale() > 2 ||
                form.getMethod() == null ||
                form.getMethod() == PaymentMethod.CREDIT) {

            throw new IllegalArgumentException(
                    "Select a customer, positive payment " +
                    "and Cash or GCash");
        }

        Customer customer = customers
                .findLockedById(form.getCustomerId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Customer not found"));

        customer.pay(form.getAmount());

        CreditPayment payment = new CreditPayment(
                customer,
                form.getAmount(),
                form.getMethod()
        );

        payment.assignCashier(cashier);

        payments.save(payment);
    }

    // ========================================
    // TOTAL UNPAID UTANG
    // ========================================

    @Transactional(readOnly = true)
    public BigDecimal outstanding() {

        return allCustomers()
                .stream()
                .map(Customer::getBalance)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    // ========================================
    // TODAY'S TOTAL SALES
    // ========================================

    @Transactional(readOnly = true)
    public BigDecimal todaySales() {

        LocalDate date = LocalDate.now();

        return sales
                .findBySoldAtGreaterThanEqualAndSoldAtLessThan(
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()
                )
                .stream()
                .map(Sale::getTotal)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    // ========================================
    // TODAY'S MONEY COLLECTED
    // ========================================

    @Transactional(readOnly = true)
    public BigDecimal todayCollected() {

        LocalDate date = LocalDate.now();

        // Cash and GCash sales.
        BigDecimal direct = sales
                .findBySoldAtGreaterThanEqualAndSoldAtLessThan(
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()
                )
                .stream()
                .filter(s ->
                        s.getPaymentMethod() != PaymentMethod.CREDIT)
                .map(Sale::getTotal)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        // Cash and GCash utang repayments.
        BigDecimal repayment = payments
                .findByPaidAtGreaterThanEqualAndPaidAtLessThan(
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()
                )
                .stream()
                .map(CreditPayment::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return direct.add(repayment);
    }
}
