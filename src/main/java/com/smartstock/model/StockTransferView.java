
package com.smartstock.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockTransferView(
        Long id,
        String productName,
        Long sourceLocationId,
        String sourceLocationName,
        Long destinationLocationId,
        String destinationLocationName,
        BigDecimal dispatchedKg,
        BigDecimal receivedKg,
        BigDecimal discrepancyKg,
        String senderName,
        String receiverName,
        StockTransferStatus status,
        boolean resolved,
        LocalDateTime dispatchedAt,
        LocalDateTime receivedAt
) {
}
