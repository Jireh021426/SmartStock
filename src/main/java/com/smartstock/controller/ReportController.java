
package com.smartstock.controller;

import com.smartstock.service.ReportService;

import jakarta.servlet.http.HttpSession;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class ReportController {

    private static final String SELECTED_DATE_KEY =
            "smartstock.dashboard.selectedDate";

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            Model model,
            HttpSession session) {

        LocalDate selectedDate = date;

        if (selectedDate != null) {

            // Remember the date selected by this user.
            session.setAttribute(
                    SELECTED_DATE_KEY,
                    selectedDate
            );

        } else {

            // Restore the previously selected date.
            Object savedDate = session.getAttribute(
                    SELECTED_DATE_KEY
            );

            if (savedDate instanceof LocalDate) {
                selectedDate = (LocalDate) savedDate;
            } else {
                selectedDate = LocalDate.now();
            }
        }

        // Load the report for the selected date.
        model.addAllAttributes(
                reports.daily(selectedDate)
        );

        // Keep the date picker synchronized.
        model.addAttribute("date", selectedDate);

        return "dashboard";
    }

    @PostMapping("/expenses")
    public String addExpense(
            @RequestParam String category,
            @RequestParam BigDecimal amount,
            @RequestParam String description,
            RedirectAttributes flash) {

        try {

            reports.addExpense(
                    category,
                    amount,
                    description
            );

            flash.addFlashAttribute(
                    "notice",
                    "Expense recorded"
            );

        } catch (IllegalArgumentException e) {

            flash.addFlashAttribute(
                    "error",
                    e.getMessage()
            );
        }

        return "redirect:/dashboard";
    }
}
