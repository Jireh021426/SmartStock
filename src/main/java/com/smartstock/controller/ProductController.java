
package com.smartstock.controller;

import com.smartstock.model.Product;
import com.smartstock.model.ProductCatalogView;
import com.smartstock.model.ProductForm;
import com.smartstock.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/home")
    public String home() {
        return "redirect:/products";
    }

    @GetMapping("/products")
    public String list(Model model, Authentication authentication) {

        boolean admin = hasRole(authentication, "ADMIN");
        boolean manager = hasRole(authentication, "MANAGER");
        boolean cashier = hasRole(authentication, "CASHIER");
        boolean auditor = hasRole(authentication, "AUDITOR");

        boolean canSeePrice =
                admin || manager || cashier || auditor;

        boolean canSeeFinancials =
                admin || manager || auditor;

        List<ProductCatalogView> catalog = service.findAll()
                .stream()
                .map(product -> toCatalogView(
                        product,
                        canSeePrice,
                        canSeeFinancials))
                .toList();

        model.addAttribute("products", catalog);
        model.addAttribute("totalKg", service.totalStockKg());

        if (canSeeFinancials) {
            model.addAttribute(
                    "totalValue",
                    service.totalStockValue());
        }

        return "products";
    }

    private ProductCatalogView toCatalogView(
            Product product,
            boolean canSeePrice,
            boolean canSeeFinancials) {

        return new ProductCatalogView(
                product.getId(),
                product.getSku(),
                product.getName(),
                String.valueOf(product.getCategory()),
                canSeePrice ? product.getPrice() : null,
                canSeeFinancials ? product.getCostPerKg() : null,
                product.getStockKg(),
                product.getLowStockKg(),
                product.isLowStock()
        );
    }

    private boolean hasRole(
            Authentication authentication,
            String role) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            return false;
        }

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        ("ROLE_" + role).equals(
                                authority.getAuthority()));
    }

    @GetMapping("/products/new")
    public String newForm(Model model) {
        model.addAttribute("productForm", new ProductForm());
        model.addAttribute("editing", false);
        return "product-form";
    }

    @GetMapping("/products/{id}/edit")
    public String editForm(
            @PathVariable Long id,
            Model model) {

        model.addAttribute(
                "productForm",
                service.toForm(service.findById(id)));

        model.addAttribute("editing", true);
        model.addAttribute("productId", id);

        return "product-form";
    }

    @PostMapping("/products/save")
    public String create(
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult result,
            Model model) {

        return save(null, form, result, model);
    }

    @PostMapping("/products/{id}/save")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("productForm") ProductForm form,
            BindingResult result,
            Model model) {

        return save(id, form, result, model);
    }

    private String save(
            Long id,
            ProductForm form,
            BindingResult result,
            Model model) {

        if (!result.hasErrors()) {
            try {
                if (id == null) {
                    service.create(form);
                } else {
                    service.update(id, form);
                }

                return "redirect:/products";

            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }

        model.addAttribute("editing", id != null);
        model.addAttribute("productId", id);

        return "product-form";
    }

    @PostMapping("/products/{id}/delete")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes attrs) {

        try {
            service.delete(id);

        } catch (IllegalArgumentException ex) {
            attrs.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/products";
    }
}
