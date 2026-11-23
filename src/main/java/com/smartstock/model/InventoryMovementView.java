
package com.smartstock.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

public record InventoryMovementView(
        Long id,
        String productName,
        MovementType type,
        BigDecimal weightKg,
        String reason,
        LocalDateTime recordedAt,
        BigDecimal purchaseCostPerKg,
        BigDecimal totalPurchaseCost,
        BigDecimal averageCostAfter,
        BigDecimal inventoryLossValue,
        LocalDate businessDate,
        String supplierName,
        String deliveryReceiptNumber
) {
}
