
package com.smartstock.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResolutionView(
        Long id,
        Long transferId,
        BigDecimal recoveredKg,
        BigDecimal confirmedLossKg,
        BigDecimal lossValue,
        String resolvedByName,
        LocalDateTime resolvedAt,
        String explanation
) {
}
