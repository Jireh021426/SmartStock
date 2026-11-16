package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class RemittanceService {

    private final EmployeeRepository employees;
    private final SaleRepository sales;
    private final CreditPaymentRepository payments;
    private final RemittanceRepository remittances;

    public RemittanceService(
            EmployeeRepository employees,
            SaleRepository sales,
            CreditPaymentRepository payments,
            RemittanceRepository remittances) {

        this.employees = employees;
        this.sales = sales;
        this.payments = payments;
        this.remittances = remittances;
    }

    // ========================================
    // EMPLOYEE VALIDATION
    // ========================================

    private Employee employee(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee login required");
        }

        return employees
                .findByUsername(username.toLowerCase(Locale.ROOT))
                .filter(Employee::isActive)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Active employee account required"));
    }

    // ========================================
    // CASHIER LOCK FOR REMITTANCE SUBMISSION
    // ========================================

    private Employee lockedCashier(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Cashier login required");
        }

        Employee cashier = employees
                .findLockedByUsername(
                        username.toLowerCase(Locale.ROOT))
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Active cashier account required"));

        if (!cashier.isActive() ||
                cashier.getRole() != EmployeeRole.CASHIER) {

            throw new IllegalArgumentException(
                    "Only active cashiers may submit remittances");
        }

        StockLocation assigned = cashier.getAssignedLocation();

        if (assigned == null ||
                !assigned.isActive() ||
                "LEGACY".equalsIgnoreCase(assigned.getCode()) ||
                !"STALL".equalsIgnoreCase(
                        assigned.getLocationType())) {

            throw new IllegalArgumentException(
                    "Cashier requires an active STALL assignment");
        }

        return cashier;
    }

    // ========================================
    // EXPECTED REMITTANCE
    // ========================================

    public record Expected(
            BigDecimal cash,
            BigDecimal gcash
    ) {}

    @Transactional(readOnly = true)
    public Expected expected(Employee cashier, LocalDate date) {

        var start = date.atStartOfDay();
        var end = date.plusDays(1).atStartOfDay();

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal gcash = BigDecimal.ZERO;

        // Include direct sales.
        for (Sale sale :
                sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(
                        start, end)) {

            if (sale.getCashier() == null ||
                    !sale.getCashier().getId()
                            .equals(cashier.getId())) {
                continue;
            }

            if (sale.getPaymentMethod() == PaymentMethod.CASH) {
                cash = cash.add(sale.getTotal());
            }

            if (sale.getPaymentMethod() == PaymentMethod.GCASH) {
                gcash = gcash.add(sale.getTotal());
            }
        }

        // Include customer credit repayments.
        for (CreditPayment payment :
                payments.findByPaidAtGreaterThanEqualAndPaidAtLessThan(
                        start, end)) {

            if (payment.getCashier() == null ||
                    !payment.getCashier().getId()
                            .equals(cashier.getId())) {
                continue;
            }

            if (payment.getMethod() == PaymentMethod.CASH) {
                cash = cash.add(payment.getAmount());
            }

            if (payment.getMethod() == PaymentMethod.GCASH) {
                gcash = gcash.add(payment.getAmount());
            }
        }

        return new Expected(cash, gcash);
    }

    // ========================================
    // CURRENT EMPLOYEE
    // ========================================

    @Transactional(readOnly = true)
    public Employee current(String username) {
        return employee(username);
    }

    // Like current(), but returns empty instead of throwing.
    // Used for the built-in in-memory "admin" login, which has
    // no row in the employees table.
    @Transactional(readOnly = true)
    public Optional<Employee> findActive(String username) {

        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return employees
                .findByUsername(username.toLowerCase(Locale.ROOT))
                .filter(Employee::isActive);
    }

    // Every remittance, newest first (read-only view for the
    // built-in admin).
    @Transactional(readOnly = true)
    public List<Remittance> all() {
        return remittances.findAllByOrderBySubmittedAtDesc();
    }

    // ========================================
    // VISIBLE REMITTANCES
    // ========================================

    @Transactional(readOnly = true)
    public List<Remittance> visible(String username) {

        Employee e = employee(username);

        return e.getRole() == EmployeeRole.CASHIER
                ? remittances
                        .findByCashierIdOrderBySubmittedAtDesc(
                                e.getId())
                : remittances
                        .findAllByOrderBySubmittedAtDesc();
    }

    // ========================================
    // CHECK SUBMISSION STATUS
    // ========================================

    @Transactional(readOnly = true)
    public boolean submitted(Employee cashier, LocalDate date) {

        return remittances
                .findByCashierIdAndBusinessDate(
                        cashier.getId(), date)
                .isPresent();
    }

    // ========================================
    // SUBMIT DAILY REMITTANCE
    // ========================================

    @Transactional
    public Remittance submit(
            String username,
            BigDecimal declaredCash,
            BigDecimal declaredGcash) {

        // Acquire the same cashier lock used by
        // SalesService.recordSale() and recordPayment().
        //
        // This serializes financial operations for
        // the same cashier until transaction completion.
        Employee cashier = lockedCashier(username);

        validateAmount(declaredCash, "Declared cash");
        validateAmount(declaredGcash, "Declared GCash");

        LocalDate date = LocalDate.now();

        // Check while holding the cashier lock.
        if (submitted(cashier, date)) {
            throw new IllegalArgumentException(
                    "Today's remittance already submitted");
        }

        // Calculate expected amounts while the cashier
        // lock prevents other cooperating transactions
        // from recording sales or repayments.
        Expected e = expected(cashier, date);

        return remittances.saveAndFlush(
                new Remittance(
                        cashier,
                        cashier.getAssignedLocation(),
                        date,
                        e.cash(),
                        e.gcash(),
                        declaredCash,
                        declaredGcash
                )
        );
    }

    // ========================================
    // VERIFY DAILY REMITTANCE
    // ========================================

    @Transactional
    public void verify(
            String username,
            Long id,
            BigDecimal countedCash,
            BigDecimal confirmedGcash,
            String note) {

        Employee reviewer = employee(username);

        if (reviewer.getRole() != EmployeeRole.ADMIN &&
                reviewer.getRole() != EmployeeRole.MANAGER) {

            throw new IllegalArgumentException(
                    "Manager approval required");
        }

        validateAmount(countedCash, "Counted cash");
        validateAmount(confirmedGcash, "Confirmed GCash");

        // Lock the remittance before reviewing.
        Remittance r = remittances.findByIdForUpdate(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Remittance not found"));

        if (r.getStatus() != Remittance.Status.SUBMITTED) {
            throw new IllegalStateException(
                    "Remittance already reviewed");
        }

        // Detect any discrepancy between the submitted
        // snapshot and current transaction records.
        Expected live = expected(
                r.getCashier(),
                r.getBusinessDate()
        );

        if (live.cash().compareTo(r.getExpectedCash()) != 0 ||
                live.gcash().compareTo(r.getExpectedGcash()) != 0) {

            throw new IllegalStateException(
                    "New transactions were recorded after submission. " +
                    "Reconciliation requires investigation.");
        }

        r.review(
                reviewer,
                countedCash,
                confirmedGcash,
                note
        );
    }

    // ========================================
    // VALIDATE MONEY AMOUNTS
    // ========================================

    private void validateAmount(
            BigDecimal amount,
            String label) {

        if (amount == null ||
                amount.signum() < 0 ||
                amount.scale() > 2 ||
                amount.precision() - amount.scale() > 12) {

            throw new IllegalArgumentException(
                    label +
                    " must be nonnegative, with at most two decimals");
        }
    }
}