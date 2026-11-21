package com.smartstock.repository;

import com.smartstock.model.HistoricalDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface HistoricalDayRepository extends JpaRepository<HistoricalDay, Long> {
    List<HistoricalDay> findAllByOrderByBusinessDateDescIdDesc();
    Optional<HistoricalDay> findByLocationIdAndBusinessDate(Long locationId,LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from HistoricalDay d where d.id = :id")
    Optional<HistoricalDay> findLockedById(@Param("id") Long id);
}
