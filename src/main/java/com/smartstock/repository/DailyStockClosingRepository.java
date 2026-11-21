package com.smartstock.repository;

import com.smartstock.model.DailyStockClosing;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DailyStockClosingRepository extends JpaRepository<DailyStockClosing, Long> {
    List<DailyStockClosing> findByBusinessDateOrderByIdDesc(LocalDate businessDate);
    List<DailyStockClosing> findByBusinessDateAndLocationIdAndProductIdOrderByIdDesc(
            LocalDate businessDate, Long locationId, Long productId);
}
