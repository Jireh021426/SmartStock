
package com.smartstock.controller;

import com.smartstock.model.*;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.repository.EmployeeRepository;
import com.smartstock.service.*;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Controller
public class SalesController {

    private final EmployeeRepository employees;
    private final SalesService sales;
    private final ProductService products;
    private final StockLocationRepository locations;

    public SalesController(
            SalesService sales,
            EmployeeRepository employees,
            ProductService products,
            StockLocationRepository locations) {

        this.sales = sales;
        this.employees = employees;
        this.products = products;
        this.locations = locations;
    }

    @GetMapping("/sales")
    public String index(Model model) {

        model.addAttribute("sales", sales.allSales());
        model.addAttribute("todaySales", sales.todaySales());
        model.addAttribute("todayCollected", sales.todayCollected());
        model.addAttribute("outstanding", sales.outstanding());

        return "sales";
    }

    @GetMapping("/sales/new")
    public String newSale(Model model, Authentication authentication) {

        model.addAttribute("saleForm", new SaleForm());
        fillSaleOptions(model, authentication);

        return "sale-form";
    }

    @PostMapping("/sales/save")
    public String save(
            @Valid @ModelAttribute("saleForm") SaleForm form,
            BindingResult errors,
            Model model,
            Authentication authentication) {

        if (errors.hasErrors()) {
            fillSaleOptions(model, authentication);
            return "sale-form";
        }

        try {
            Sale sale = sales.recordSale(
                    authentication.getName(), form);

            return "redirect:/sales/" + sale.getId();

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            model.addAttribute("error", ex.getMessage());
            fillSaleOptions(model, authentication);

            return "sale-form";
        }
    }

    @GetMapping("/sales/{id}")
    public String receipt(
            @PathVariable Long id,
            Model model) {

        try {
            model.addAttribute("sale", sales.sale(id));
            return "receipt";

        } catch (IllegalArgumentException ex) {

            if ("Sale not found".equals(ex.getMessage())) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sale receipt not found");
            }

            throw ex;
        }
    }

    @GetMapping("/customers")
    public String customers(Model model) {

        model.addAttribute("customers", sales.allCustomers());
        model.addAttribute("payments", sales.allPayments());
        model.addAttribute("outstanding", sales.outstanding());

        return "customers";
    }

    @GetMapping("/customers/new")
    public String customerForm(Model model) {

        model.addAttribute("customerForm", new CustomerForm());

        return "customer-form";
    }

    @PostMapping("/customers/save")
    public String createCustomer(
            @Valid @ModelAttribute("customerForm") CustomerForm form,
            BindingResult errors,
            Model model) {

        if (errors.hasErrors()) {
            return "customer-form";
        }

        try {
            sales.createCustomer(form);
            return "redirect:/customers";

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());
            return "customer-form";
        }
    }

    @GetMapping("/payments/new")
    public String paymentForm(Model model) {

        model.addAttribute("paymentForm", new PaymentForm());
        fillPaymentOptions(model);

        return "payment-form";
    }

    @PostMapping("/payments/save")
    public String payment(
            @Valid @ModelAttribute("paymentForm") PaymentForm form,
            BindingResult errors,
            Model model,
            Authentication authentication) {

        if (errors.hasErrors()) {
            fillPaymentOptions(model);
            return "payment-form";
        }

        try {
            sales.recordPayment(
                    authentication.getName(), form);

            return "redirect:/customers";

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            model.addAttribute("error", ex.getMessage());
            fillPaymentOptions(model);

            return "payment-form";
        }
    }

    private void fillSaleOptions(
            Model model,
            Authentication authentication) {

        Employee cashier = employees.findByUsername(
                authentication.getName().toLowerCase(Locale.ROOT))
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Cashier account not found."));

        if (!cashier.isActive()
                || cashier.getRole() != EmployeeRole.CASHIER
                || cashier.getAssignedLocation() == null
                || !cashier.getAssignedLocation().isActive()
                || !"STALL".equalsIgnoreCase(
                        cashier.getAssignedLocation()
                                .getLocationType())) {

            throw new IllegalArgumentException(
                    "An active cashier with a STALL assignment is required.");
        }

        model.addAttribute(
                "assignedLocation",
                cashier.getAssignedLocation());

        model.addAttribute("products", products.findAll());

        model.addAttribute(
                "locations",
                java.util.List.of(cashier.getAssignedLocation()));

        model.addAttribute(
                "customers",
                sales.allCustomers());
    }

    private void fillPaymentOptions(Model model) {

        model.addAttribute(
                "customers",
                sales.allCustomers());
    }
}
