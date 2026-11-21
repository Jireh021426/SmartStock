
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Model model,
            Authentication authentication) {

        boolean admin = hasRole(authentication, "ADMIN");
        boolean manager = hasRole(authentication, "MANAGER");
        boolean auditor = hasRole(authentication, "AUDITOR");

        boolean canSeeFinancials =
                admin || manager || auditor;

        // Only allow supported page sizes.
        int pageSize = List.of(5, 10, 25, 50).contains(size)
                ? size : 5;

        int safePage = Math.max(0, page);

        Pageable pageable = PageRequest.of(safePage, pageSize);

        Page<StockMovement> movementPage =
                inventory.history(pageable);

        // Handle a page number beyond the last available page.
        if (movementPage.getTotalPages() > 0
                && safePage >= movementPage.getTotalPages()) {

            safePage = movementPage.getTotalPages() - 1;
            pageable = PageRequest.of(safePage, pageSize);
            movementPage = inventory.history(pageable);
        }

        List<InventoryMovementView> movements =
                movementPage.getContent()
                        .stream()
                        .map(movement -> toView(
                                movement,
                                canSeeFinancials))
                        .toList();

        long totalRecords = movementPage.getTotalElements();

        model.addAttribute("movements", movements);
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", movementPage.getTotalPages());
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageSizes", List.of(5, 10, 25, 50));
        model.addAttribute("totalMovements", totalRecords);

        // Show a maximum of seven numbered pages.
        int totalPages = movementPage.getTotalPages();

        int windowStart = Math.max(
                0,
                Math.min(safePage - 3, totalPages - 7)
        );

        int windowEnd = Math.min(totalPages, windowStart + 7);

        model.addAttribute(
                "pageNumbers",
                java.util.stream.IntStream
                        .range(windowStart, windowEnd)
                        .boxed()
                        .toList()
        );

        model.addAttribute(
                "showingStart",
                totalRecords == 0 ? 0 : safePage * pageSize + 1);

        model.addAttribute(
                "showingEnd",
                Math.min((safePage + 1L) * pageSize, totalRecords));

        model.addAttribute(
                "stockInRecords",
                inventory.countByType(MovementType.STOCK_IN));

        model.addAttribute(
                "stockOutRecords",
                inventory.countByType(MovementType.STOCK_OUT));

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
                        : null,
                movement.getBusinessDate()
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
