
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class StockTransferService {

    private final StockTransferRepository transfers;
    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository inventories;
    private final EmployeeRepository employees;
    private final StockMovementRepository movements;
    private final TransferResolutionRepository resolutions;

    public StockTransferService(
            StockTransferRepository transfers,
            ProductRepository products,
            StockLocationRepository locations,
            LocationInventoryRepository inventories,
            EmployeeRepository employees,
            StockMovementRepository movements,
            TransferResolutionRepository resolutions) {

        this.transfers = transfers;
        this.products = products;
        this.locations = locations;
        this.inventories = inventories;
        this.employees = employees;
        this.movements = movements;
        this.resolutions = resolutions;
    }

    @Transactional(readOnly = true)
    public List<StockTransfer> history() {
        return transfers.findAllByOrderByDispatchedAtDescIdDesc();
    }

    @Transactional
    public Long dispatch(
            String username,
            Long productId,
            Long destinationId,
            BigDecimal weightKg,
            String note) {

        Employee sender = activeEmployee(username);

        if (sender.getRole() != EmployeeRole.SENDER) {
            throw new IllegalArgumentException(
                "Only assigned senders may dispatch stock.");
        }

        StockLocation source = sender.getAssignedLocation();

        if (source == null || !source.isActive()) {
            throw new IllegalArgumentException(
                "Sender has no active assigned location.");
        }

        if (productId == null || destinationId == null) {
            throw new IllegalArgumentException(
                "Product and destination are required.");
        }

        validatePositiveWeight(weightKg);

        Product product = products.findLockedById(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Product not found."));

        StockLocation destination = locations.findById(destinationId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Destination not found."));

        if (!destination.isActive()) {
            throw new IllegalArgumentException(
                "Destination is inactive.");
        }

        if (source.getId().equals(destination.getId())) {
            throw new IllegalArgumentException(
                "Cannot transfer stock to the same location.");
        }

        LocationInventory sourceBalance = inventories
                .findLockedByLocationAndProduct(
                    source.getId(),
                    product.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "No stock exists at the sender's location."));

        BigDecimal weight = weightKg.setScale(3);

        if (sourceBalance.getStockKg().compareTo(weight) < 0) {
            throw new IllegalArgumentException(
                "Insufficient source stock. Available: " +
                sourceBalance.getStockKg() + " kg.");
        }

        sourceBalance.changeStock(weight.negate());

        StockTransfer transfer = new StockTransfer(
            product,
            source,
            destination,
            sender,
            weight,
            note
        );

        transfers.saveAndFlush(transfer);

        StockMovement movement = new StockMovement(
            product,
            MovementType.STOCK_OUT,
            weight,
            "Transfer #" + transfer.getId() +
                " dispatched to " + destination.getCode()
        );

        movement.assignLocation(source);
        movement.assignCategory(MovementCategory.STOCK_TRANSFER);
        movements.save(movement);

        // Internal transfers do not change company-wide stock.
        return transfer.getId();
    }

    @Transactional
    public void receive(
            String username,
            Long transferId,
            BigDecimal actualKg,
            String note) {

        Employee receiver = activeEmployee(username);

        if (receiver.getRole() != EmployeeRole.RECEIVER) {
            throw new IllegalArgumentException(
                "Only assigned receivers may confirm stock.");
        }

        if (transferId == null) {
            throw new IllegalArgumentException(
                "Transfer ID is required.");
        }

        validateNonnegativeWeight(actualKg);

        StockTransfer initial = transfers.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Transfer not found."));

        Product product = products.findLockedById(
                initial.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Product not found."));

        StockTransfer transfer = transfers.findLockedById(transferId)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Transfer not found."));

        if (transfer.getStatus() != StockTransferStatus.DISPATCHED) {
            throw new IllegalArgumentException(
                "Transfer is already confirmed.");
        }

        StockLocation destination =
                transfer.getDestinationLocation();

        StockLocation assigned = receiver.getAssignedLocation();

        if (assigned == null ||
                !assigned.getId().equals(destination.getId())) {
            throw new IllegalArgumentException(
                "Receiver is not assigned to this destination.");
        }

        if (!destination.isActive()) {
            throw new IllegalArgumentException(
                "Destination is inactive.");
        }

        if (transfer.getSender().getId().equals(receiver.getId())) {
            throw new IllegalArgumentException(
                "Sender cannot confirm their own transfer.");
        }

        if (actualKg.compareTo(transfer.getDispatchedKg()) > 0) {
            throw new IllegalArgumentException(
                "Received quantity exceeds dispatched quantity.");
        }

        BigDecimal received = actualKg.setScale(3);

        LocationInventory destinationBalance = inventories
                .findLockedByLocationAndProduct(
                    destination.getId(),
                    product.getId())
                .orElse(null);

        if (destinationBalance == null) {
            destinationBalance = new LocationInventory(
                destination, product);
        }

        transfer.confirmReceipt(receiver, received, note);

        if (received.signum() > 0) {

            destinationBalance.changeStock(received);
            inventories.save(destinationBalance);

            StockMovement movement = new StockMovement(
                product,
                MovementType.STOCK_IN,
                received,
                "Transfer #" + transfer.getId() +
                    " received from " +
                    transfer.getSourceLocation().getCode()
            );

            movement.assignLocation(destination);
            movement.assignCategory(MovementCategory.STOCK_TRANSFER);
            movements.save(movement);
        }

        // The original discrepancy is retained for investigation.
    }

    @Transactional(readOnly = true)
    public BigDecimal unresolvedKg(Long productId) {

        Set<Long> resolvedIds = new HashSet<>();

        for (TransferResolution resolution : resolutions.findAll()) {
            resolvedIds.add(resolution.getTransfer().getId());
        }

        return history().stream()
                .filter(t ->
                    t.getProduct().getId().equals(productId))
                .map(t -> {

                    if (t.getStatus() ==
                            StockTransferStatus.DISPATCHED) {
                        return t.getDispatchedKg();
                    }

                    if (t.getStatus() ==
                            StockTransferStatus.DISCREPANCY &&
                            !resolvedIds.contains(t.getId())) {
                        return t.getDiscrepancyKg();
                    }

                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3);
    }

    private Employee activeEmployee(String username) {

        if (username == null) {
            throw new IllegalArgumentException(
                "Employee login required.");
        }

        Employee employee = employees.findByUsername(
                username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException(
                    "A database employee account is required."));

        if (!employee.isActive()) {
            throw new IllegalArgumentException(
                "Employee account is inactive.");
        }

        return employee;
    }

    private void validatePositiveWeight(BigDecimal value) {

        if (value == null ||
                value.signum() <= 0 ||
                value.scale() > 3) {
            throw new IllegalArgumentException(
                "Weight must be positive, with at most 3 decimals.");
        }
    }

    private void validateNonnegativeWeight(BigDecimal value) {

        if (value == null ||
                value.signum() < 0 ||
                value.scale() > 3) {
            throw new IllegalArgumentException(
                "Received weight cannot be negative and must have at most 3 decimals.");
        }
    }
}
