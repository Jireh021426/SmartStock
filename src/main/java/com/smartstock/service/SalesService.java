
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

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
    public List<Customer> retailCustomers() {
        return customers.findByCustomerTypeOrderByNameAsc(CustomerType.RETAIL);
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
        // This existing cashier-only entry point intentionally ignores any
        // cashierId supplied by the browser.
        return recordSaleForCashier(cashier, form, username);
    }

    @Transactional
    public Sale recordSaleAsAdmin(Authentication authentication, SaleForm form) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a ->
                        "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Administrator role required");
        }
        if (form == null || form.getCashierId() == null) {
            throw new IllegalArgumentException(
                    "Choose the cashier responsible for this sale and remittance");
        }
        Employee cashier = employees.findLockedById(form.getCashierId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected cashier account not found"));
        validateCashier(cashier);
        return recordSaleForCashier(cashier, form, authentication.getName());
    }

    private Sale recordSaleForCashier(
            Employee cashier, SaleForm form, String recordedBy) {
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
            if (customer.isIndependentStall()) {
                throw new IllegalArgumentException(
                        "Independent-stall deliveries must use the Independent Stalls module");
            }
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
        sale.assignRecordedBy(recordedBy);

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

        validateCashier(cashier);
        return cashier;
    }

    private void validateCashier(Employee cashier) {
        if (cashier == null || !cashier.isActive() ||
                cashier.getRole() != EmployeeRole.CASHIER) {
            throw new IllegalArgumentException(
                    "An active cashier is required for sale recording.");
        }

        StockLocation assigned = cashier.getAssignedLocation();

        if (assigned == null ||
                !assigned.isActive() ||
                "LEGACY".equalsIgnoreCase(assigned.getCode()) ||
                !"STALL".equalsIgnoreCase(assigned.getLocationType())) {

            throw new IllegalArgumentException(
                    "Cashier requires an active STALL assignment.");
        }
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
        if (form == null ||
                form.getRequestId() == null ||
                form.getRequestId().isBlank() ||
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

        // The cashier row is locked by requireCashier(), serializing
        // retries from the same cashier with remittance submission.
        // Check the key BEFORE the remittance-day check: a retry after
        // a successful payment must not be interpreted as a new payment.
        String requestId;
        try {
            requestId = java.util.UUID.fromString(form.getRequestId()).toString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid payment request ID");
        }
        var previous = payments.findByRequestId(requestId);
        if (previous.isPresent()) {
            CreditPayment existing = previous.get();
            if (existing.getCashier() == null ||
                    !existing.getCashier().getId().equals(cashier.getId()) ||
                    !existing.getCustomer().getId().equals(form.getCustomerId()) ||
                    existing.getAmount().compareTo(form.getAmount()) != 0 ||
                    existing.getMethod() != form.getMethod()) {
                throw new IllegalArgumentException("Payment request ID already used for different payment details");
            }
            return;
        }

        requireOpenRemittanceDay(cashier);

        Customer customer = customers
                .findLockedById(form.getCustomerId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Customer not found"));

        if (customer.isIndependentStall()) {
            throw new IllegalArgumentException(
                    "Independent-stall collections must use the Independent Stalls module");
        }

        customer.pay(form.getAmount());

        CreditPayment payment = new CreditPayment(
                customer,
                form.getAmount(),
                form.getMethod()
        );

        payment.assignCashier(cashier);
        payment.assignRequestId(requestId);

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

        LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));

        return java.util.stream.Stream.concat(
                sales.findByBusinessDate(date).stream(),
                sales.findByBusinessDateIsNullAndSoldAtGreaterThanEqualAndSoldAtLessThan(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                )
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

        LocalDate date = LocalDate.now(java.time.ZoneId.of("Asia/Manila"));

        // Cash and GCash sales.
        BigDecimal direct = java.util.stream.Stream.concat(
                sales.findByBusinessDate(date).stream(),
                sales.findByBusinessDateIsNullAndSoldAtGreaterThanEqualAndSoldAtLessThan(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                )
                .filter(s ->
                        s.getPaymentMethod() != PaymentMethod.CREDIT)
                .map(Sale::getTotal)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        // Cash and GCash utang repayments.
        BigDecimal repayment = java.util.stream.Stream.concat(
                payments.findByBusinessDate(date).stream(),
                payments.findByBusinessDateIsNullAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                )
                .map(CreditPayment::getAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );

        return direct.add(repayment);
    }
}
