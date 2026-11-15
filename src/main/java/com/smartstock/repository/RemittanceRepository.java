
package com.smartstock.repository;

import com.smartstock.model.Remittance;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RemittanceRepository
        extends JpaRepository<Remittance, Long> {

    Optional<Remittance> findByCashierIdAndBusinessDate(
            Long cashierId,
            LocalDate businessDate
    );

    List<Remittance> findAllByOrderBySubmittedAtDesc();

    List<Remittance> findByCashierIdOrderBySubmittedAtDesc(
            Long cashierId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Remittance r WHERE r.id = :id")
    Optional<Remittance> findByIdForUpdate(
            @Param("id") Long id
    );
}
