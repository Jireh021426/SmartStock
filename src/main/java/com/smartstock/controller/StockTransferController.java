
package com.smartstock.controller;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.*;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Controller
@RequestMapping("/transfers")
public class StockTransferController {

    private final StockTransferService transfers;
    private final TransferResolutionService resolutionService;
    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final EmployeeRepository employees;

    public StockTransferController(
            StockTransferService transfers,
            TransferResolutionService resolutionService,
            ProductRepository products,
            StockLocationRepository locations,
            EmployeeRepository employees) {

        this.transfers = transfers;
        this.resolutionService = resolutionService;
        this.products = products;
        this.locations = locations;
        this.employees = employees;
    }

    @GetMapping
    public String index(
            Authentication authentication,
            Model model) {

        Employee employee = employees.findByUsername(
                authentication.getName().toLowerCase(Locale.ROOT)
        ).orElse(null);

        boolean sender = employee != null
                && employee.isActive()
                && employee.getRole() == EmployeeRole.SENDER;

        boolean receiver = employee != null
                && employee.isActive()
                && employee.getRole() == EmployeeRole.RECEIVER;

        boolean admin = hasRole(authentication, "ADMIN");
        boolean managerRole = hasRole(authentication, "MANAGER");
        boolean auditor = hasRole(authentication, "AUDITOR");

        boolean manager = admin || managerRole;
        boolean privileged = manager || auditor;

        StockLocation assignedLocation = null;

        if (employee != null
                && employee.isActive()
                && employee.getAssignedLocation() != null) {

            Long locationId =
                    employee.getAssignedLocation().getId();

            assignedLocation = locations.findById(locationId)
                    .orElse(null);
        }

        Long assignedLocationId =
                assignedLocation == null
                        ? null
                        : assignedLocation.getId();

        String assignedLocationName =
                assignedLocation == null
                        ? "Not assigned"
                        : assignedLocation.getName();

        List<StockTransfer> visibleEntities =
                transfers.history()
                        .stream()
                        .filter(transfer ->
                                privileged
                                || isRelatedToLocation(
                                        transfer,
                                        assignedLocationId
                                )
                        )
                        .toList();

        Set<Long> visibleTransferIds = new HashSet<>();

        for (StockTransfer transfer : visibleEntities) {
            visibleTransferIds.add(transfer.getId());
        }

        List<TransferResolution> visibleResolutions =
                resolutionService.history()
                        .stream()
                        .filter(resolution ->
                                visibleTransferIds.contains(
                                        resolution.getTransfer().getId()
                                )
                        )
                        .toList();

        Set<Long> resolvedTransferIds = new HashSet<>();

        for (TransferResolution resolution : visibleResolutions) {
            resolvedTransferIds.add(
                    resolution.getTransfer().getId()
            );
        }

        List<StockTransferView> transferViews =
                visibleEntities.stream()
                        .map(transfer ->
                                toTransferView(
                                        transfer,
                                        resolvedTransferIds
                                )
                        )
                        .toList();

        List<TransferResolutionView> resolutionViews =
                visibleResolutions.stream()
                        .map(resolution ->
                                toResolutionView(
                                        resolution,
                                        privileged
                                )
                        )
                        .toList();

        List<TransferProductView> productViews =
                sender
                        ? products.findAll()
                                .stream()
                                .map(product ->
                                        new TransferProductView(
                                                product.getId(),
                                                product.getName()
                                        )
                                )
                                .toList()
                        : List.of();

        List<TransferLocationView> locationViews =
                sender
                        ? locations.findByActiveTrueOrderByNameAsc()
                                .stream()
                                .filter(location ->
                                        assignedLocationId == null
                                        || !location.getId()
                                                .equals(assignedLocationId)
                                )
                                .map(location ->
                                        new TransferLocationView(
                                                location.getId(),
                                                location.getName(),
                                                location.getCode()
                                        )
                                )
                                .toList()
                        : List.of();

        model.addAttribute("isSender", sender);
        model.addAttribute("isReceiver", receiver);
        model.addAttribute("isManager", manager);

        model.addAttribute(
                "canSeeTransferFinancials",
                privileged
        );

        model.addAttribute(
                "assignedLocationId",
                assignedLocationId
        );

        model.addAttribute(
                "assignedLocationName",
                assignedLocationName
        );

        model.addAttribute("products", productViews);
        model.addAttribute("locations", locationViews);
        model.addAttribute("transfers", transferViews);

        model.addAttribute(
                "dispatchedStatus",
                StockTransferStatus.DISPATCHED
        );

        model.addAttribute(
                "discrepancyStatus",
                StockTransferStatus.DISCREPANCY
        );

        model.addAttribute(
                "resolutionHistory",
                resolutionViews
        );

        return "transfers";
    }

    private boolean hasRole(
            Authentication authentication,
            String role) {

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority()
                                .equals("ROLE_" + role)
                );
    }

    private boolean isRelatedToLocation(
            StockTransfer transfer,
            Long locationId) {

        if (locationId == null) {
            return false;
        }

        return locationId.equals(
                transfer.getSourceLocation().getId()
        ) || locationId.equals(
                transfer.getDestinationLocation().getId()
        );
    }

    private StockTransferView toTransferView(
            StockTransfer transfer,
            Set<Long> resolvedTransferIds) {

        return new StockTransferView(
                transfer.getId(),
                transfer.getProduct().getName(),
                transfer.getSourceLocation().getId(),
                transfer.getSourceLocation().getName(),
                transfer.getDestinationLocation().getId(),
                transfer.getDestinationLocation().getName(),
                transfer.getDispatchedKg(),
                transfer.getReceivedKg(),
                transfer.getDiscrepancyKg(),
                transfer.getSender().getFullName(),
                transfer.getReceiver() == null
                        ? null
                        : transfer.getReceiver().getFullName(),
                transfer.getStatus(),
                resolvedTransferIds.contains(transfer.getId()),
                transfer.getDispatchedAt(),
                transfer.getReceivedAt()
        );
    }

    private TransferResolutionView toResolutionView(
            TransferResolution resolution,
            boolean canSeeFinancials) {

        return new TransferResolutionView(
                resolution.getId(),
                resolution.getTransfer().getId(),
                resolution.getRecoveredKg(),
                resolution.getConfirmedLossKg(),
                canSeeFinancials
                        ? resolution.getLossValue()
                        : null,
                resolution.getResolvedBy().getFullName(),
                resolution.getResolvedAt(),
                resolution.getExplanation()
        );
    }

    @PostMapping("/dispatch")
    public String dispatch(
            Authentication authentication,
            @RequestParam Long productId,
            @RequestParam Long destinationId,
            @RequestParam BigDecimal weightKg,
            @RequestParam(required = false) String note,
            RedirectAttributes redirect) {

        try {

            Long id = transfers.dispatch(
                    authentication.getName(),
                    productId,
                    destinationId,
                    weightKg,
                    note
            );

            redirect.addFlashAttribute(
                    "notice",
                    "Transfer #" + id
                            + " dispatched. Awaiting receiver confirmation."
            );

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/transfers";
    }

    @PostMapping("/receive")
    public String receive(
            Authentication authentication,
            @RequestParam Long transferId,
            @RequestParam BigDecimal actualKg,
            @RequestParam(required = false) String note,
            RedirectAttributes redirect) {

        try {

            transfers.receive(
                    authentication.getName(),
                    transferId,
                    actualKg,
                    note
            );

            redirect.addFlashAttribute(
                    "notice",
                    "Transfer #" + transferId
                            + " confirmation recorded."
            );

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/transfers";
    }

    @PostMapping("/resolve")
    public String resolve(
            Authentication authentication,
            @RequestParam Long transferId,
            @RequestParam BigDecimal recoveredKg,
            @RequestParam BigDecimal confirmedLossKg,
            @RequestParam String explanation,
            RedirectAttributes redirect) {

        try {

            Long id = resolutionService.resolve(
                    authentication.getName(),
                    transferId,
                    recoveredKg,
                    confirmedLossKg,
                    explanation
            );

            redirect.addFlashAttribute(
                    "notice",
                    "Investigation #" + id
                            + " closed. Original transfer record preserved."
            );

        } catch (IllegalArgumentException |
                 IllegalStateException ex) {

            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/transfers";
    }
}
