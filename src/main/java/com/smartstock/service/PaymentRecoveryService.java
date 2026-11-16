package com.smartstock.service;

import com.smartstock.model.CreditPayment;
import com.smartstock.model.PaymentForm;
import com.smartstock.repository.CreditPaymentRepository;
import com.smartstock.repository.EmployeeRepository;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentRecoveryService {

    private final SalesService sales;
    private final CreditPaymentRepository payments;
    private final EmployeeRepository employees;

    public PaymentRecoveryService(
            SalesService sales,
            CreditPaymentRepository payments,
            EmployeeRepository employees) {

        this.sales = sales;
        this.payments = payments;
        this.employees = employees;
    }

    @Transactional(propagation = Propagation.NEVER)
    public void recordPayment(
            String username,
            PaymentForm form) {

        try {
            // SalesService owns the actual payment transaction.
            sales.recordPayment(username, form);

        } catch (DataIntegrityViolationException ex) {

            String requestId = null;

            if (form != null && form.getRequestId() != null) {
                try {
                    requestId = UUID.fromString(
                            form.getRequestId()
                    ).toString();
                } catch (IllegalArgumentException ignored) {
                    throw ex;
                }
            }

            if (requestId == null) {
                throw ex;
            }

            // The failed payment transaction has rolled back.
            // Only a committed matching request ID permits
            // translation to a controlled conflict.
            CreditPayment existing = payments
                    .findByRequestId(requestId)
                    .orElse(null);

            if (existing == null) {
                throw ex;
            }

            var cashier = username == null
                    ? null
                    : employees.findByUsername(
                            username.toLowerCase(Locale.ROOT)
                    ).orElse(null);

            boolean matchingDetails =
                    cashier != null &&
                    existing.getCashier() != null &&
                    existing.getCashier().getId().equals(cashier.getId()) &&
                    form.getCustomerId() != null &&
                    existing.getCustomer().getId().equals(form.getCustomerId()) &&
                    form.getAmount() != null &&
                    existing.getAmount().compareTo(form.getAmount()) == 0 &&
                    existing.getMethod() == form.getMethod();

            if (!matchingDetails) {
                throw new IllegalArgumentException(
                        "Payment request ID already used for different payment details",
                        ex
                );
            }

            throw new IllegalArgumentException(
                    "Payment request ID already used. " +
                    "Check payment history before trying again.",
                    ex
            );
        }
    }
}
