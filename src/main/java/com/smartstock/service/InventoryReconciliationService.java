
package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class InventoryReconciliationService {

    private final StockLocationRepository locations;
    private final LocationInventoryRepository inventories;
    private final ProductRepository products;
    private final StockTransferRepository transfers;
    private final TransferResolutionRepository resolutions;

    public InventoryReconciliationService(
            StockLocationRepository locations,
            LocationInventoryRepository inventories,
            ProductRepository products,
            StockTransferRepository transfers,
            TransferResolutionRepository resolutions) {

        this.locations = locations;
        this.inventories = inventories;
        this.products = products;
        this.transfers = transfers;
        this.resolutions = resolutions;
    }

    public record LocationBalance(
            Long locationId,
            String locationCode,
            String locationName,
            String productName,
            BigDecimal stockKg) {
    }

    public record ProductReconciliation(
            String productName,
            BigDecimal companyStockKg,
            BigDecimal locationStockKg,
            BigDecimal inTransitKg,
            BigDecimal unresolvedKg,
            BigDecimal accountedKg,
            BigDecimal differenceKg,
            boolean balanced) {
    }

    public record ReconciliationReport(
            List<LocationBalance> balances,
            List<ProductReconciliation> products,
            boolean allBalanced) {
    }

    @Transactional(readOnly = true)
    public ReconciliationReport generate() {

        List<StockLocation> allLocations = locations.findAll();
        List<Product> allProducts = products.findAll();
        List<LocationInventory> allInventories = inventories.findAll();

        List<StockTransfer> allTransfers =
                transfers.findAllByOrderByDispatchedAtDescIdDesc();

        Set<Long> resolvedTransferIds = new HashSet<>();

        for (TransferResolution resolution : resolutions.findAll()) {
            resolvedTransferIds.add(
                resolution.getTransfer().getId());
        }

        Map<Long, Map<Long, BigDecimal>> stockByLocation =
                new LinkedHashMap<>();

        for (LocationInventory inventory : allInventories) {

            Long locationId = inventory.getLocation().getId();
            Long productId = inventory.getProduct().getId();

            stockByLocation
                    .computeIfAbsent(
                        locationId,
                        id -> new LinkedHashMap<>())
                    .put(productId, inventory.getStockKg());
        }

        List<LocationBalance> balances = new ArrayList<>();
        List<ProductReconciliation> productReports =
                new ArrayList<>();

        boolean allBalanced = true;

        for (Product product : allProducts) {

            BigDecimal locationTotal = BigDecimal.ZERO;

            for (StockLocation location : allLocations) {

                BigDecimal quantity = stockByLocation
                        .getOrDefault(
                            location.getId(),
                            Map.of())
                        .getOrDefault(
                            product.getId(),
                            BigDecimal.ZERO);

                balances.add(new LocationBalance(
                    location.getId(),
                    location.getCode(),
                    location.getName(),
                    product.getName(),
                    quantity
                ));

                locationTotal = locationTotal.add(quantity);
            }

            BigDecimal inTransit = BigDecimal.ZERO;
            BigDecimal unresolved = BigDecimal.ZERO;

            for (StockTransfer transfer : allTransfers) {

                if (!transfer.getProduct().getId()
                        .equals(product.getId())) {
                    continue;
                }

                if (transfer.getStatus()
                        == StockTransferStatus.DISPATCHED) {

                    inTransit = inTransit.add(
                        transfer.getDispatchedKg());

                } else if (transfer.getStatus()
                        == StockTransferStatus.DISCREPANCY &&
                        !resolvedTransferIds.contains(
                            transfer.getId())) {

                    unresolved = unresolved.add(
                        transfer.getDiscrepancyKg());
                }
            }

            BigDecimal accounted = locationTotal
                    .add(inTransit)
                    .add(unresolved);

            BigDecimal companyStock = product.getStockKg();

            BigDecimal difference =
                    companyStock.subtract(accounted);

            boolean balanced =
                    difference.compareTo(BigDecimal.ZERO) == 0;

            if (!balanced) {
                allBalanced = false;
            }

            productReports.add(new ProductReconciliation(
                product.getName(),
                companyStock,
                locationTotal,
                inTransit,
                unresolved,
                accounted,
                difference,
                balanced
            ));
        }

        return new ReconciliationReport(
            balances,
            productReports,
            allBalanced
        );
    }
}
