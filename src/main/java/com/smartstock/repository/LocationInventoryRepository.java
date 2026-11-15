
package com.smartstock.repository;

import com.smartstock.model.LocationInventory;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LocationInventoryRepository
        extends JpaRepository<LocationInventory, Long> {

    Optional<LocationInventory> findByLocationIdAndProductId(
            Long locationId,
            Long productId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT li
           FROM LocationInventory li
           WHERE li.location.id = :locationId
           AND li.product.id = :productId
           """)
    Optional<LocationInventory> findLockedByLocationAndProduct(
            @Param("locationId") Long locationId,
            @Param("productId") Long productId
    );

    List<LocationInventory> findByLocationId(Long locationId);

    List<LocationInventory> findByProductId(Long productId);

    @Query("""
           SELECT COALESCE(SUM(li.stockKg), 0)
           FROM LocationInventory li
           WHERE li.product.id = :productId
           """)
    BigDecimal sumStockByProductId(
            @Param("productId") Long productId
    );

    boolean existsByProductId(Long productId);
}
