
package com.smartstock.controller;

import com.smartstock.model.Supplier;
import com.smartstock.service.SupplierService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService suppliers;

    public SupplierController(SupplierService suppliers) {
        this.suppliers = suppliers;
    }

    private void requireAdmin(Authentication authentication) {
        boolean isAdmin = authentication != null
                && authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority()));

        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping
    public String index(
            Model model,
            Authentication authentication) {

        requireAdmin(authentication);

        model.addAttribute("suppliers", suppliers.allSuppliers());

        return "suppliers";
    }

    @GetMapping("/new")
    public String newSupplier(
            Model model,
            Authentication authentication) {

        requireAdmin(authentication);

        fillForm(model, false, null, "", "", "", "");

        return "supplier-form";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam(defaultValue = "") String contactNumber,
            @RequestParam(defaultValue = "") String address,
            Authentication authentication,
            Model model,
            RedirectAttributes redirect) {

        requireAdmin(authentication);

        try {
            suppliers.createSupplier(
                    code,
                    name,
                    contactNumber,
                    address);

            redirect.addFlashAttribute(
                    "success",
                    "Supplier registered successfully.");

            return "redirect:/suppliers";

        } catch (IllegalArgumentException ex) {

            fillForm(
                    model,
                    false,
                    null,
                    code,
                    name,
                    contactNumber,
                    address);

            model.addAttribute("error", ex.getMessage());

            return "supplier-form";

        } catch (DataIntegrityViolationException ex) {

            fillForm(
                    model,
                    false,
                    null,
                    code,
                    name,
                    contactNumber,
                    address);

            model.addAttribute(
                    "error",
                    "Supplier could not be saved. "
                    + "The supplier code may already exist.");

            return "supplier-form";
        }
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            Model model,
            Authentication authentication) {

        requireAdmin(authentication);

        Supplier supplier = findOr404(id);

        fillForm(
                model,
                true,
                id,
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactNumber(),
                supplier.getAddress());

        return "supplier-form";
    }

    @PostMapping("/{id}/save")
    public String update(
            @PathVariable Long id,
            @RequestParam String code,
            @RequestParam String name,
            @RequestParam(defaultValue = "") String contactNumber,
            @RequestParam(defaultValue = "") String address,
            Authentication authentication,
            Model model,
            RedirectAttributes redirect) {

        requireAdmin(authentication);

        try {
            suppliers.updateSupplier(
                    id,
                    code,
                    name,
                    contactNumber,
                    address);

            redirect.addFlashAttribute(
                    "success",
                    "Supplier updated successfully.");

            return "redirect:/suppliers";

        } catch (IllegalArgumentException ex) {

            fillForm(
                    model,
                    true,
                    id,
                    code,
                    name,
                    contactNumber,
                    address);

            model.addAttribute("error", ex.getMessage());

            return "supplier-form";

        } catch (DataIntegrityViolationException ex) {

            fillForm(
                    model,
                    true,
                    id,
                    code,
                    name,
                    contactNumber,
                    address);

            model.addAttribute(
                    "error",
                    "Supplier could not be updated. "
                    + "The supplier code may already exist.");

            return "supplier-form";
        }
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirect) {

        requireAdmin(authentication);

        try {
            suppliers.deactivateSupplier(id);

            redirect.addFlashAttribute(
                    "success",
                    "Supplier deactivated successfully.");

        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/suppliers";
    }

    @PostMapping("/{id}/activate")
    public String activate(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes redirect) {

        requireAdmin(authentication);

        try {
            suppliers.activateSupplier(id);

            redirect.addFlashAttribute(
                    "success",
                    "Supplier activated successfully.");

        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/suppliers";
    }

    private Supplier findOr404(Long id) {
        try {
            return suppliers.findSupplier(id);

        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    ex.getMessage());
        }
    }

    private void fillForm(
            Model model,
            boolean editing,
            Long supplierId,
            String code,
            String name,
            String contactNumber,
            String address) {

        model.addAttribute("editing", editing);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("code", code);
        model.addAttribute("name", name);
        model.addAttribute("contactNumber", contactNumber);
        model.addAttribute("address", address);
    }
}
