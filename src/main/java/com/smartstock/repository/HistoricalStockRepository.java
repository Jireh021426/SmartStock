package com.smartstock.repository;

import com.smartstock.model.HistoricalStock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HistoricalStockRepository extends JpaRepository<HistoricalStock,Long> {
    List<HistoricalStock> findByDayIdOrderByProductNameAsc(Long dayId);
    boolean existsByDayIdAndProductKey(Long dayId,String productKey);
}
