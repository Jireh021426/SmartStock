
package com.smartstock.controller;

import com.smartstock.model.InventoryMovementView;
import com.smartstock.model.MovementForm;
import com.smartstock.model.MovementType;
import com.smartstock.model.StockLocation;
import com.smartstock.model.StockMovement;

import com.smartstock.repository.StockLocationRepository;

import com.smartstock.service.InventoryService;
import com.smartstock.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventory;
    private final ProductService products;
    private final StockLocationRepository locations;

    public InventoryController(
            InventoryService inventory,
            ProductService products,
            StockLocationRepository locations) {

        this.inventory = inventory;
        this.products = products;
        this.locations = locations;
    }

    @GetMapping
    public String history(
            Model model,
            Authentication authentication) {

        boolean admin = hasRole(authentication, "ADMIN");
        boolean manager = hasRole(authentication, "MANAGER");
        boolean auditor = hasRole(authentication, "AUDITOR");

        boolean canSeeFinancials =
                admin || manager || auditor;

        List<InventoryMovementView> movements =
                inventory.history()
                        .stream()
                        .map(movement -> toView(
                                movement,
                                canSeeFinancials))
                        .toList();

        model.addAttribute("movements", movements);
        model.addAttribute(
                "canSeeInventoryFinancials",
                canSeeFinancials);
        model.addAttribute(
                "canRecordMovement",
                admin);

        return "inventory";
    }

    private InventoryMovementView toView(
            StockMovement movement,
            boolean canSeeFinancials) {

        return new InventoryMovementView(
                movement.getId(),
                movement.getProduct().getName(),
                movement.getType(),
                movement.getWeightKg(),
                movement.getReason(),
                movement.getRecordedAt(),
                canSeeFinancials
                        ? movement.getPurchaseCostPerKg()
                        : null,
                canSeeFinancials
                        ? movement.getTotalPurchaseCost()
                        : null,
                canSeeFinancials
                        ? movement.getAverageCostAfter()
                        : null,
                canSeeFinancials
                        ? movement.getInventoryLossValue()
                        : null
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

    private void loadFormOptions(Model model) {

        model.addAttribute(
                "products",
                products.findAll());

        List<StockLocation> activeLocations =
                locations.findByActiveTrueOrderByNameAsc();

        model.addAttribute(
                "locations",
                activeLocations);
    }

    @GetMapping("/new")
    public String form(
            @RequestParam(required = false) Long productId,
            Model model) {

        MovementForm form = new MovementForm();

        form.setProductId(productId);
        form.setType(MovementType.STOCK_IN);

        model.addAttribute("movementForm", form);

        loadFormOptions(model);

        return "movement-form";
    }

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute("movementForm")
            MovementForm form,
            BindingResult result,
            Model model) {

        if (form.getType() == MovementType.STOCK_IN &&
                form.getPurchaseCostPerKg() == null) {

            result.rejectValue(
                    "purchaseCostPerKg",
                    "required",
                    "Purchase cost per kg is required for stock-in");
        }

        if (form.getLocationId() != null) {

            StockLocation location = locations
                    .findById(form.getLocationId())
                    .orElse(null);

            if (location == null || !location.isActive()) {

                result.rejectValue(
                        "locationId",
                        "invalid",
                        "Please select a valid active stock location");
            }
        }

        if (result.hasErrors()) {

            loadFormOptions(model);

            return "movement-form";
        }

        try {

            inventory.record(form);

            return "redirect:/inventory";

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            model.addAttribute(
                    "error",
                    ex.getMessage());

            loadFormOptions(model);

            return "movement-form";
        }
    }
}
