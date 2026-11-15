
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class TransferResolutionService {

    private final TransferResolutionRepository resolutions;
    private final StockTransferRepository transfers;
    private final ProductRepository products;
    private final LocationInventoryRepository inventories;
    private final EmployeeRepository employees;
    private final StockMovementRepository movements;

    public TransferResolutionService(
            TransferResolutionRepository resolutions,
            StockTransferRepository transfers,
            ProductRepository products,
            LocationInventoryRepository inventories,
            EmployeeRepository employees,
            StockMovementRepository movements) {

        this.resolutions = resolutions;
        this.transfers = transfers;
        this.products = products;
        this.inventories = inventories;
        this.employees = employees;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<TransferResolution> history() {
        return resolutions.findAllByOrderByResolvedAtDescIdDesc();
    }

    @Transactional
    public Long resolve(
            String username,
            Long transferId,
            BigDecimal recoveredKg,
            BigDecimal confirmedLossKg,
            String explanation) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                "Manager login is required.");
        }

        Employee manager = employees.findByUsername(
                username.toLowerCase(Locale.ROOT)
        ).orElseThrow(() -> new IllegalArgumentException(
                "An active database manager or administrator account is required."));

        if (!manager.isActive() ||
                (manager.getRole() != EmployeeRole.MANAGER &&
                 manager.getRole() != EmployeeRole.ADMIN)) {
            throw new IllegalArgumentException(
                "Only active managers and administrators can resolve discrepancies.");
        }

        if (transferId == null) {
            throw new IllegalArgumentException(
                "Transfer ID is required.");
        }

        validateWeight(recoveredKg);
        validateWeight(confirmedLossKg);

        if (explanation == null ||
                explanation.isBlank() ||
                explanation.trim().length() > 1000) {
            throw new IllegalArgumentException(
                "An investigation explanation of at most 1000 characters is required.");
        }

        StockTransfer initial = transfers.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Transfer not found."));

        // Maintain the existing lock order:
        // product, transfer, then location inventory.
        Product product = products.findLockedById(
                initial.getProduct().getId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "Product not found."));

        StockTransfer transfer = transfers.findLockedById(transferId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Transfer not found."));

        if (transfer.getStatus() != StockTransferStatus.DISCREPANCY) {
            throw new IllegalArgumentException(
                "This transfer does not have an unresolved receipt discrepancy.");
        }

        if (resolutions.existsByTransferId(transferId)) {
            throw new IllegalArgumentException(
                "This discrepancy has already been resolved.");
        }

        if (recoveredKg.add(confirmedLossKg)
                .compareTo(transfer.getDiscrepancyKg()) != 0) {
            throw new IllegalArgumentException(
                "Recovered weight plus confirmed loss must equal " +
                transfer.getDiscrepancyKg() + " kg.");
        }

        StockLocation destination = transfer.getDestinationLocation();

        if (recoveredKg.signum() > 0) {

            if (!destination.isActive()) {
                throw new IllegalArgumentException(
                    "Destination location is inactive. Reactivate it before recording recovered stock.");
            }

            LocationInventory balance = inventories
                    .findLockedByLocationAndProduct(
                        destination.getId(),
                        product.getId())
                    .orElse(null);

            if (balance == null) {
                balance = new LocationInventory(
                    destination, product);
            }

            balance.changeStock(recoveredKg);
            inventories.save(balance);

            StockMovement recovery = new StockMovement(
                product,
                MovementType.STOCK_IN,
                recoveredKg,
                "Transfer #" + transferId + " recovered stock"
            );

            recovery.assignLocation(destination);
            recovery.assignCategory(MovementCategory.STOCK_TRANSFER);
            movements.save(recovery);
        }

        BigDecimal lossValue = BigDecimal.ZERO.setScale(2);

        if (confirmedLossKg.signum() > 0) {

            lossValue = confirmedLossKg
                    .multiply(product.getCostPerKg())
                    .setScale(2, RoundingMode.HALF_UP);

            product.changeStock(confirmedLossKg.negate());

            StockMovement loss = new StockMovement(
                product,
                MovementType.STOCK_OUT,
                confirmedLossKg,
                "Transfer #" + transferId +
                    " confirmed missing stock"
            );

            // No location is assigned because the shortage
            // was already removed from the source at dispatch
            // and never entered the destination balance.
            loss.assignCategory(MovementCategory.MISSING_STOCK);
            loss.setInventoryLossValue(product.getCostPerKg());
            movements.save(loss);
        }

        TransferResolution resolution = new TransferResolution(
                transfer,
                manager,
                recoveredKg,
                confirmedLossKg,
                lossValue,
                explanation
        );

        return resolutions.saveAndFlush(resolution).getId();
    }

    private static void validateWeight(BigDecimal value) {

        if (value == null ||
                value.signum() < 0 ||
                value.scale() > 3) {
            throw new IllegalArgumentException(
                "Weights must be nonnegative with at most 3 decimal places.");
        }
    }
}
