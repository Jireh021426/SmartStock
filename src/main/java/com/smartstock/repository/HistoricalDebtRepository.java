package com.smartstock.repository;

import com.smartstock.model.HistoricalDebt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface HistoricalDebtRepository extends JpaRepository<HistoricalDebt, Long> {
    List<HistoricalDebt> findByDayIdOrderByCustomerNameAsc(Long dayId);
    boolean existsByDayIdAndCustomerKey(Long dayId,String customerKey);
}
