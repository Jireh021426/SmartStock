
package com.smartstock.repository;

import com.smartstock.model.StockTransfer;
import com.smartstock.model.StockTransferStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockTransferRepository
        extends JpaRepository<StockTransfer, Long> {

    @Query("""
            SELECT t
            FROM StockTransfer t
            LEFT JOIN FETCH t.product
            LEFT JOIN FETCH t.sourceLocation
            LEFT JOIN FETCH t.destinationLocation
            LEFT JOIN FETCH t.sender
            LEFT JOIN FETCH t.receiver
            ORDER BY t.dispatchedAt DESC, t.id DESC
            """)
    List<StockTransfer> findAllByOrderByDispatchedAtDescIdDesc();

    List<StockTransfer> findByStatusOrderByDispatchedAtDesc(
            StockTransferStatus status
    );

    boolean existsByStatusAndSourceLocationIdOrStatusAndDestinationLocationId(
            StockTransferStatus sourceStatus,
            Long sourceId,
            StockTransferStatus destinationStatus,
            Long destinationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM StockTransfer t
            WHERE t.id = :id
            """)
    Optional<StockTransfer> findLockedById(
            @Param("id") Long id
    );
}
