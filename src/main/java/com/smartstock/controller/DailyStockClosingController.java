package com.smartstock.controller;

import com.smartstock.model.*;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.service.DailyStockClosingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.ZoneId;

@Controller
@RequestMapping("/daily-closing")
public class DailyStockClosingController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private final DailyStockClosingService closings;
    private final ProductRepository products;
    private final StockLocationRepository locations;

    public DailyStockClosingController(DailyStockClosingService closings,
                                       ProductRepository products,
                                       StockLocationRepository locations) {
        this.closings = closings;
        this.products = products;
        this.locations = locations;
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private void fill(Model model, Authentication auth, LocalDate date) {
        model.addAttribute("report", closings.summary(date));
        model.addAttribute("products", products.findAll());
        model.addAttribute("locations", locations.findByActiveTrueOrderByNameAsc().stream()
                .filter(l -> "STALL".equalsIgnoreCase(l.getLocationType()))
                .toList());
        model.addAttribute("today", LocalDate.now(ZONE));
        model.addAttribute("canCount", hasRole(auth,"ADMIN") || hasRole(auth,"MANAGER") || hasRole(auth,"AUDITOR"));
        model.addAttribute("canReview", hasRole(auth,"ADMIN") || hasRole(auth,"MANAGER"));
    }

    @GetMapping
    public String list(@RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       Model model, Authentication auth) {
        LocalDate selected = date == null ? LocalDate.now(ZONE) : date;
        DailyClosingForm form = new DailyClosingForm();
        form.setBusinessDate(LocalDate.now(ZONE));
        model.addAttribute("closingForm", form);
        fill(model, auth, selected);
        return "daily-closing";
    }

    @PostMapping("/count")
    public String count(@Valid @ModelAttribute("closingForm") DailyClosingForm form,
                        BindingResult validation, Model model, Authentication auth,
                        RedirectAttributes flash) {
        if (validation.hasErrors()) {
            fill(model, auth, LocalDate.now(ZONE));
            return "daily-closing";
        }
        try {
            Long id = closings.submit(auth, form);
            flash.addFlashAttribute("notice", "Daily closing #" + id + " submitted for review.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/daily-closing?date=" + LocalDate.now(ZONE);
    }

    @PostMapping("/review")
    public String review(@RequestParam Long id,
                         @RequestParam String decision,
                         @RequestParam String note,
                         Authentication auth, RedirectAttributes flash) {
        try {
            if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
                throw new IllegalArgumentException("Choose approve or reject");
            }
            closings.review(auth, id, "APPROVE".equals(decision), note);
            flash.addFlashAttribute("notice", "Daily closing #" + id + " reviewed.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/daily-closing";
    }
}
