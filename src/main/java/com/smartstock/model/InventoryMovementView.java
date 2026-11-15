
package com.smartstock.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        BigDecimal inventoryLossValue
) {
}
