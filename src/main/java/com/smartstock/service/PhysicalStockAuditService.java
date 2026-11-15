
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class PhysicalStockAuditService {

    private final PhysicalStockAuditRepository audits;
    private final EmployeeRepository employees;
    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository inventories;
    private final StockMovementRepository movements;

    public PhysicalStockAuditService(
            PhysicalStockAuditRepository audits,
            EmployeeRepository employees,
            ProductRepository products,
            StockLocationRepository locations,
            LocationInventoryRepository inventories,
            StockMovementRepository movements) {

        this.audits = audits;
        this.employees = employees;
        this.products = products;
        this.locations = locations;
        this.inventories = inventories;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<PhysicalStockAudit> history() {
        return audits.findAllByOrderByCountedAtDescIdDesc();
    }

    @Transactional
    public Long count(
            String username,
            Long productId,
            Long locationId,
            BigDecimal countedKg,
            String note) {

        Employee auditor = employee(username);

        if (auditor.getRole() != EmployeeRole.AUDITOR) {
            throw new IllegalArgumentException(
                    "Only an auditor can submit physical counts.");
        }

        validateWeight(countedKg);

        if (productId == null || locationId == null) {
            throw new IllegalArgumentException(
                    "Product and location are required.");
        }

        validateNote(note);

        Product product = products.findLockedById(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found."));

        StockLocation location = locations.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Location not found."));

        validateLocation(location);

        if (audits.existsByLocationIdAndProductIdAndStatus(
                locationId,
                productId,
                PhysicalStockAudit.Status.PENDING)) {

            throw new IllegalStateException(
                    "A pending audit already exists for this product and location.");
        }

        BigDecimal expected = inventories
                .findLockedByLocationAndProduct(locationId, productId)
                .map(LocationInventory::getStockKg)
                .orElse(BigDecimal.ZERO.setScale(3));

        PhysicalStockAudit audit = new PhysicalStockAudit(
                product,
                location,
                auditor,
                expected,
                countedKg,
                note);

        return audits.saveAndFlush(audit).getId();
    }

    @Transactional
    public void review(
            String username,
            Long auditId,
            boolean approve,
            String note) {

        Employee manager = employee(username);

        if (manager.getRole() != EmployeeRole.MANAGER
                && manager.getRole() != EmployeeRole.ADMIN) {

            throw new IllegalArgumentException(
                    "Only managers and administrators can review audits.");
        }

        validateNote(note);

        if (auditId == null) {
            throw new IllegalArgumentException(
                    "Audit ID is required.");
        }

        PhysicalStockAudit initial = audits.findById(auditId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Audit not found."));

        // Lock product before the audit and location balance.
        Product product = products.findLockedById(
                initial.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found."));

        PhysicalStockAudit audit = audits.findLockedById(auditId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Audit not found."));

        if (audit.getStatus() != PhysicalStockAudit.Status.PENDING) {
            throw new IllegalStateException(
                    "Audit already reviewed.");
        }

        if (audit.getAuditor().getId().equals(manager.getId())) {
            throw new IllegalArgumentException(
                    "An auditor cannot approve their own count.");
        }

        StockLocation location = audit.getLocation();
        validateLocation(location);

        LocationInventory balance = inventories
                .findLockedByLocationAndProduct(
                        location.getId(),
                        product.getId())
                .orElse(null);

        BigDecimal current = balance == null
                ? BigDecimal.ZERO.setScale(3)
                : balance.getStockKg();

        if (approve) {

            if (current.compareTo(audit.getExpectedKg()) != 0) {
                throw new IllegalStateException(
                        "Location inventory changed after the count. " +
                        "Reject this audit and perform a new physical count.");
            }

            BigDecimal variance = audit.getVarianceKg();

            if (variance.signum() != 0) {

                if (variance.signum() > 0 && balance == null) {
                    balance = new LocationInventory(location, product);
                }

                if (balance == null) {
                    throw new IllegalStateException(
                            "Location inventory record is missing.");
                }

                // Capture cost before modifying the inventory.
                BigDecimal costAtApproval = product.getCostPerKg();

                balance.changeStock(variance);
                product.changeStock(variance);

                inventories.save(balance);

                boolean shortage = variance.signum() < 0;

                StockMovement movement = new StockMovement(
                        product,
                        shortage
                                ? MovementType.STOCK_OUT
                                : MovementType.STOCK_IN,
                        variance.abs(),
                        "Physical audit #" + auditId +
                                " approved adjustment");

                movement.assignLocation(location);

                if (shortage) {

                    // Approved physical shortages are losses,
                    // not internal transfers or sales.
                    movement.assignCategory(
                            MovementCategory.MISSING_STOCK);

                    // Preserve the monetary value at approval.
                    movement.setInventoryLossValue(
                            costAtApproval);

                } else {

                    // Positive count variance increases stock.
                    // It must not be reported as a loss.
                    movement.assignCategory(
                            MovementCategory.STOCK_ADJUSTMENT);
                }

                movements.save(movement);
            }
        }

        audit.review(manager, approve, note);
    }

    private Employee employee(String username) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Login required.");
        }

        Employee result = employees.findByUsername(
                username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException(
                        "An active database employee account is required."));

        if (!result.isActive()) {
            throw new IllegalArgumentException(
                    "Employee account is inactive.");
        }

        return result;
    }

    private void validateLocation(StockLocation location) {

        if (!location.isActive()) {
            throw new IllegalArgumentException(
                    "Location is inactive.");
        }

        if ("LEGACY".equalsIgnoreCase(location.getCode())) {
            throw new IllegalArgumentException(
                    "Historical LEGACY inventory cannot be physically audited.");
        }
    }

    private void validateWeight(BigDecimal weight) {

        if (weight == null
                || weight.signum() < 0
                || weight.scale() > 3) {

            throw new IllegalArgumentException(
                    "Count must be nonnegative with at most 3 decimals.");
        }
    }

    private void validateNote(String note) {

        if (note == null
                || note.isBlank()
                || note.trim().length() > 1000) {

            throw new IllegalArgumentException(
                    "An explanation of 1–1000 characters is required.");
        }
    }
}
