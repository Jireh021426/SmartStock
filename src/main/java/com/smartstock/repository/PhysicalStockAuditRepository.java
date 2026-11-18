
package com.smartstock.repository;

import com.smartstock.model.PhysicalStockAudit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhysicalStockAuditRepository
        extends JpaRepository<PhysicalStockAudit, Long> {

    List<PhysicalStockAudit> findAllByOrderByCountedAtDescIdDesc();

    long countByStatus(PhysicalStockAudit.Status status);



    boolean existsByLocationIdAndProductIdAndStatus(
            Long locationId,
            Long productId,
            PhysicalStockAudit.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PhysicalStockAudit a where a.id = :id")
    Optional<PhysicalStockAudit> findLockedById(@Param("id") Long id);
}
