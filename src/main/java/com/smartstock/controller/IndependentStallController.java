package com.smartstock.controller;

import com.smartstock.model.*;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.service.IndependentStallService;
import com.smartstock.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/independent-stalls")
public class IndependentStallController {
    private final IndependentStallService service;
    private final ProductService products;
    private final StockLocationRepository locations;

    public IndependentStallController(IndependentStallService service,
                                     ProductService products,
                                     StockLocationRepository locations) {
        this.service = service;
        this.products = products;
        this.locations = locations;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("stalls", service.stalls());
        model.addAttribute("deliveries", service.deliveries());
        model.addAttribute("collections", service.collections());
        model.addAttribute("outstanding", service.outstanding());
        return "independent-stalls";
    }

    @GetMapping("/new")
    public String newStall(Model model) {
        model.addAttribute("stallForm", new IndependentStallForm());
        return "independent-stall-form";
    }

    @PostMapping("/save")
    public String saveStall(@Valid @ModelAttribute("stallForm") IndependentStallForm form,
                           BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return "independent-stall-form";
        try {
            service.register(form);
            flash.addFlashAttribute("notice", "Independent stall registered");
            return "redirect:/independent-stalls";
        } catch (IllegalArgumentException | DataIntegrityViolationException ex) {
            model.addAttribute("error", "Unable to register stall. Check the contact identifier is unique.");
            return "independent-stall-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editStall(@PathVariable Long id, Model model) {
        Customer stall;
        try {
            stall = service.stall(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Independent stall not found");
        }
        IndependentStallForm form = new IndependentStallForm();
        form.setName(stall.getName());
        form.setPhone(stall.getPhone());
        model.addAttribute("stallId", stall.getId());
        model.addAttribute("balance", stall.getBalance());
        model.addAttribute("stallForm", form);
        return "independent-stall-edit";
    }

    @PostMapping("/{id}/update")
    public String updateStall(@PathVariable Long id,
                              @Valid @ModelAttribute("stallForm") IndependentStallForm form,
                              BindingResult errors, Model model, RedirectAttributes flash) {
        Customer stall;
        try {
            stall = service.stall(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Independent stall not found");
        }
        model.addAttribute("stallId", id);
        model.addAttribute("balance", stall.getBalance());
        if (errors.hasErrors()) {
            return "independent-stall-edit";
        }
        try {
            service.updateStall(id, form);
            flash.addFlashAttribute("notice", "Independent stall information updated");
            return "redirect:/independent-stalls";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("error", "Contact / account identifier is already in use.");
        }
        return "independent-stall-edit";
    }

    @GetMapping("/deliveries/new")
    public String newDelivery(Model model) {
        model.addAttribute("deliveryForm", new IndependentDeliveryForm());
        loadDeliveryOptions(model);
        return "independent-delivery-form";
    }

    @PostMapping("/deliveries/save")
    public String saveDelivery(@Valid @ModelAttribute("deliveryForm") IndependentDeliveryForm form,
                               BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            loadDeliveryOptions(model);
            return "independent-delivery-form";
        }
        try {
            Sale sale = service.recordDelivery(form);
            flash.addFlashAttribute("notice", "Credit delivery recorded: " + sale.getDeliveryReference());
            return "redirect:/independent-stalls";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("error", "Database conflict. No delivery was recorded; check history and contact an administrator before retrying.");
        }
        loadDeliveryOptions(model);
        return "independent-delivery-form";
    }

    @GetMapping("/payments/new")
    public String newPayment(Model model) {
        model.addAttribute("stallPaymentForm", new IndependentPaymentForm());
        model.addAttribute("stalls", service.stalls());
        return "independent-payment-form";
    }

    @PostMapping("/payments/save")
    public String savePayment(@Valid @ModelAttribute("stallPaymentForm") IndependentPaymentForm form,
                              BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("stalls", service.stalls());
            return "independent-payment-form";
        }
        try {
            service.recordPayment(form);
            flash.addFlashAttribute("notice", "Payment recorded. Outstanding balance updated.");
            return "redirect:/independent-stalls";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("error", "Payment conflict. Verify the payment history before retrying.");
        }
        model.addAttribute("stalls", service.stalls());
        return "independent-payment-form";
    }

    private void loadDeliveryOptions(Model model) {
        model.addAttribute("stalls", service.stalls());
        model.addAttribute("products", products.findAll());
        model.addAttribute("locations", locations.findByActiveTrueOrderByNameAsc().stream()
                .filter(location -> !"LEGACY".equalsIgnoreCase(location.getCode()) &&
                        ("STORAGE".equalsIgnoreCase(location.getLocationType()) ||
                         "STALL".equalsIgnoreCase(location.getLocationType())))
                .toList());
    }
}
