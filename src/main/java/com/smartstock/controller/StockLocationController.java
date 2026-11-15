
package com.smartstock.controller;

import com.smartstock.model.StockLocationForm;
import com.smartstock.service.StockLocationService;
import com.smartstock.service.InventoryReconciliationService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/locations")
public class StockLocationController {

    private final StockLocationService locations;
    private final InventoryReconciliationService reconciliation;

    public StockLocationController(
            StockLocationService locations,
            InventoryReconciliationService reconciliation) {

        this.locations = locations;
        this.reconciliation = reconciliation;
    }

    @GetMapping
    public String index(Model model, Authentication authentication) {

        boolean isAdmin = authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority()));

        model.addAttribute("isAdmin", isAdmin);

        model.addAttribute(
                "locations",
                locations.findAll());

        model.addAttribute(
                "reconciliation",
                reconciliation.generate());

        return "locations";
    }

    @GetMapping("/new")
    public String newLocation(Model model) {

        model.addAttribute(
                "locationForm",
                new StockLocationForm());

        model.addAttribute("editing", false);

        return "location-form";
    }

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute("locationForm")
            StockLocationForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes redirect) {

        if (errors.hasErrors()) {
            model.addAttribute("editing", false);
            return "location-form";
        }

        try {

            locations.create(form);

            redirect.addFlashAttribute(
                    "success",
                    "Stock location created successfully");

            return "redirect:/locations";

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());
            model.addAttribute("editing", false);

            return "location-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            Model model) {

        model.addAttribute(
                "locationForm",
                locations.toForm(id));

        model.addAttribute("editing", true);
        model.addAttribute("locationId", id);

        return "location-form";
    }

    @PostMapping("/{id}/save")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("locationForm")
            StockLocationForm form,
            BindingResult errors,
            Model model,
            RedirectAttributes redirect) {

        if (errors.hasErrors()) {

            model.addAttribute("editing", true);
            model.addAttribute("locationId", id);

            return "location-form";
        }

        try {

            locations.update(id, form);

            redirect.addFlashAttribute(
                    "success",
                    "Stock location updated successfully");

            return "redirect:/locations";

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());
            model.addAttribute("editing", true);
            model.addAttribute("locationId", id);

            return "location-form";
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(
            @PathVariable Long id,
            RedirectAttributes redirect) {

        try {

            locations.deactivate(id);

            redirect.addFlashAttribute(
                    "success",
                    "Stock location deactivated");

        } catch (IllegalArgumentException ex) {

            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage());
        }

        return "redirect:/locations";
    }

    @PostMapping("/{id}/activate")
    public String activate(
            @PathVariable Long id,
            RedirectAttributes redirect) {

        try {

            locations.activate(id);

            redirect.addFlashAttribute(
                    "success",
                    "Stock location activated");

        } catch (IllegalArgumentException ex) {

            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage());
        }

        return "redirect:/locations";
    }
}
