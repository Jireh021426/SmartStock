package com.smartstock.controller;

import com.smartstock.model.*;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.service.HistoricalReconciliationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/historical-reconciliation")
public class HistoricalReconciliationController {
    private final HistoricalReconciliationService service;
    private final StockLocationRepository locations;
    public HistoricalReconciliationController(HistoricalReconciliationService service,
                                              StockLocationRepository locations) {
        this.service = service;
        this.locations = locations;
    }
    private boolean editable(Authentication a) {
        return a != null && a.getAuthorities().stream().anyMatch(x ->
                "ROLE_ADMIN".equals(x.getAuthority()) || "ROLE_MANAGER".equals(x.getAuthority()));
    }
    @GetMapping
    public String index(Model model, Authentication auth) {
        model.addAttribute("days", service.list(auth));
        model.addAttribute("form", new HistoricalDayForm());
        model.addAttribute("locations", locations.findByActiveTrueOrderByNameAsc().stream()
                .filter(l -> "STALL".equalsIgnoreCase(l.getLocationType()) && !"LEGACY".equalsIgnoreCase(l.getCode()))
                .toList());
        model.addAttribute("canEdit", editable(auth));
        return "historical-reconciliation";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, Authentication auth) {
        var report = service.details(auth, id);
        model.addAttribute("report", report);
        var summaryForm = new HistoricalSummaryEditForm();
        summaryForm.setCollections(report.day().getCollections());
        summaryForm.setSourceFile(report.day().getSourceFile());
        summaryForm.setNotes(report.day().getNotes());
        model.addAttribute("summaryForm", summaryForm);
        model.addAttribute("debtForm", new HistoricalDebtForm());
        model.addAttribute("stockForm", new HistoricalStockForm());
        model.addAttribute("canEdit", editable(auth));
        return "historical-reconciliation-detail";
    }
    @PostMapping
    public String create(@Valid @ModelAttribute("form") HistoricalDayForm form, BindingResult validation,
                         Authentication auth, RedirectAttributes flash) {
        if (validation.hasErrors()) { flash.addFlashAttribute("error", "Complete the date, collections, source and location fields."); return "redirect:/historical-reconciliation"; }
        try { return "redirect:/historical-reconciliation/" + service.create(auth, form); }
        catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage()); return "redirect:/historical-reconciliation";
        }
    }
    @PostMapping("/{id}/summary")
    public String summary(@PathVariable Long id, @Valid @ModelAttribute("summaryForm") HistoricalSummaryEditForm form,
                          BindingResult validation, Authentication auth, RedirectAttributes flash) {
        if (validation.hasErrors()) flash.addFlashAttribute("error", "Enter valid collection, source and note details.");
        else try { service.updateSummary(auth, id, form); flash.addFlashAttribute("notice", "Draft summary updated."); }
             catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
    @PostMapping("/{id}/debts")
    public String debt(@PathVariable Long id, @Valid @ModelAttribute HistoricalDebtForm form,
                       BindingResult validation, Authentication auth, RedirectAttributes flash) {
        if (validation.hasErrors()) flash.addFlashAttribute("error", "Enter a customer and nonnegative balance.");
        else try { service.addDebt(auth, id, form); }
             catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
    @PostMapping("/{id}/stock")
    public String stock(@PathVariable Long id, @Valid @ModelAttribute HistoricalStockForm form,
                        BindingResult validation, Authentication auth, RedirectAttributes flash) {
        if (validation.hasErrors()) flash.addFlashAttribute("error", "Enter valid stock quantities and price.");
        else try { service.addStock(auth, id, form); }
             catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
    @PostMapping("/{id}/debts/{rowId}/delete")
    public String deleteDebt(@PathVariable Long id, @PathVariable Long rowId,
                             Authentication auth, RedirectAttributes flash) {
        try { service.removeDebt(auth, id, rowId); }
        catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
    @PostMapping("/{id}/stock/{rowId}/delete")
    public String deleteStock(@PathVariable Long id, @PathVariable Long rowId,
                              Authentication auth, RedirectAttributes flash) {
        try { service.removeStock(auth, id, rowId); }
        catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
    @PostMapping("/{id}/complete")
    public String complete(@PathVariable Long id, @RequestParam String section, @RequestParam boolean complete,
                           Authentication auth, RedirectAttributes flash) {
        try { service.setComplete(auth, id, section, complete); }
        catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
    @PostMapping("/{id}/review")
    public String review(@PathVariable Long id, @RequestParam String note,
                         Authentication auth, RedirectAttributes flash) {
        try { service.review(auth, id, note); flash.addFlashAttribute("notice", "Historical evidence reviewed and locked."); }
        catch (IllegalArgumentException | IllegalStateException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/historical-reconciliation/" + id;
    }
}
