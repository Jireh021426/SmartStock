
package com.smartstock.controller;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.model.PhysicalStockAudit;
import com.smartstock.repository.EmployeeRepository;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.service.PhysicalStockAuditService;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.Locale;

@Controller
@RequestMapping("/audits")
public class PhysicalStockAuditController {

    private final PhysicalStockAuditService audits;
    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final EmployeeRepository employees;

    public PhysicalStockAuditController(
            PhysicalStockAuditService audits,
            ProductRepository products,
            StockLocationRepository locations,
            EmployeeRepository employees) {

        this.audits = audits;
        this.products = products;
        this.locations = locations;
        this.employees = employees;
    }

    @GetMapping
    public String index(Authentication auth, Model model) {

        Employee employee = employees.findByUsername(
                auth.getName().toLowerCase(Locale.ROOT))
                .orElse(null);

        boolean active = employee != null && employee.isActive();

        boolean isAuditor = active
                && employee.getRole() == EmployeeRole.AUDITOR;

        boolean isManager = active
                && (employee.getRole() == EmployeeRole.MANAGER
                || employee.getRole() == EmployeeRole.ADMIN);

        model.addAttribute("isAuditor", isAuditor);
        model.addAttribute("isManager", isManager);
        model.addAttribute("products", products.findAll());
        model.addAttribute(
                "locations",
                locations.findByActiveTrueOrderByNameAsc());
        model.addAttribute("audits", audits.history());
        model.addAttribute(
                "pendingStatus", PhysicalStockAudit.Status.PENDING);

        return "audits";
    }

    @PostMapping("/count")
    public String count(
            Authentication auth,
            @RequestParam Long productId,
            @RequestParam Long locationId,
            @RequestParam BigDecimal countedKg,
            @RequestParam String note,
            RedirectAttributes redirect) {

        try {
            Long id = audits.count(
                    auth.getName(),
                    productId,
                    locationId,
                    countedKg,
                    note);

            redirect.addFlashAttribute(
                    "notice",
                    "Physical audit #" + id +
                    " submitted for manager review.");

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/audits";
    }

    @PostMapping("/review")
    public String review(
            Authentication auth,
            @RequestParam Long auditId,
            @RequestParam String decision,
            @RequestParam String note,
            RedirectAttributes redirect) {

        try {
            if (!decision.equals("APPROVE")
                    && !decision.equals("REJECT")) {
                throw new IllegalArgumentException("Invalid decision.");
            }

            boolean approve = decision.equals("APPROVE");

            audits.review(
                    auth.getName(),
                    auditId,
                    approve,
                    note);

            redirect.addFlashAttribute(
                    "notice",
                    "Physical audit #" + auditId +
                    (approve ? " approved." : " rejected."));

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/audits";
    }
}
