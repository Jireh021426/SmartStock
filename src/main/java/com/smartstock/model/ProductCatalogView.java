
package com.smartstock.model;

import java.math.BigDecimal;

public record ProductCatalogView(
        Long id,
        String sku,
        String name,
        String category,
        BigDecimal price,
        BigDecimal costPerKg,
        BigDecimal stockKg,
        BigDecimal lowStockKg,
        boolean lowStock
) {
}
