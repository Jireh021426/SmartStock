package com.smartstock.controller;

import com.smartstock.model.EmployeeRole;
import com.smartstock.service.RemittanceService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class RemittanceController {

    private final RemittanceService service;

    public RemittanceController(RemittanceService service) {
        this.service = service;
    }

    // The built-in in-memory "admin" login (see SecurityConfig)
    // has ROLE_ADMIN but no employee record.
    private static boolean hasAdminRole(Authentication auth) {
        return auth.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @GetMapping("/remittances")
    public String index(Authentication auth, Model model) {

        // Built-in admin: no employee row, so show all
        // remittances read-only. Verifying a remittance records
        // the reviewing employee, so that needs a manager account.
        if (service.findActive(auth.getName()).isEmpty()
                && hasAdminRole(auth)) {

            model.addAttribute("cashier", false);
            model.addAttribute("reviewer", false);
            model.addAttribute("builtInAdmin", true);
            model.addAttribute("remittances", service.all());

            return "remittances";
        }

        var employee = service.current(auth.getName());

        boolean cashier =
            employee.getRole() == EmployeeRole.CASHIER;

        boolean reviewer =
            employee.getRole() == EmployeeRole.ADMIN ||
            employee.getRole() == EmployeeRole.MANAGER;

        model.addAttribute("cashier", cashier);
        model.addAttribute("reviewer", reviewer);

        model.addAttribute(
            "remittances",
            service.visible(auth.getName())
        );

        if (cashier) {
            model.addAttribute(
                "expected",
                service.expected(employee, LocalDate.now())
            );

            model.addAttribute(
                "alreadySubmitted",
                service.submitted(employee, LocalDate.now())
            );
        }

        return "remittances";
    }

    @PostMapping("/remittances/submit")
    public String submit(
            Authentication auth,
            @RequestParam BigDecimal declaredCash,
            @RequestParam BigDecimal declaredGcash,
            RedirectAttributes redirect) {

        try {
            service.submit(
                auth.getName(),
                declaredCash,
                declaredGcash
            );

            redirect.addFlashAttribute(
                "message",
                "Remittance submitted for independent review"
            );
        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            redirect.addFlashAttribute(
                "error",
                ex.getMessage()
            );
        }

        return "redirect:/remittances";
    }

    @PostMapping("/remittances/verify")
    public String verify(
            Authentication auth,
            @RequestParam Long id,
            @RequestParam BigDecimal countedCash,
            @RequestParam BigDecimal confirmedGcash,
            @RequestParam(required = false) String note,
            RedirectAttributes redirect) {

        try {
            service.verify(
                auth.getName(),
                id,
                countedCash,
                confirmedGcash,
                note
            );

            redirect.addFlashAttribute(
                "message",
                "Independent remittance verification recorded"
            );

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            redirect.addFlashAttribute(
                "error",
                ex.getMessage()
            );
        }

        return "redirect:/remittances";
    }
}