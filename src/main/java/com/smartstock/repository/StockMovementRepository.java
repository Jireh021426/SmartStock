package com.smartstock.repository;

import com.smartstock.model.StockMovement;
import com.smartstock.model.MovementType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long> {

    // Existing method retained for ReportService.
    List<StockMovement> findAllByOrderByRecordedAtDescIdDesc();

    // Paginated history, newest records first.
    Page<StockMovement> findAllByOrderByRecordedAtDescIdDesc(
            Pageable pageable);

    long countByType(MovementType type);

    boolean existsByProductIdAndLocationIdAndRecordedAtAfter(
            Long productId, Long locationId, LocalDateTime recordedAt);

    boolean existsByProductId(Long productId);
}
