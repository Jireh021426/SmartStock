
package com.smartstock.repository;

import com.smartstock.model.StockLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockLocationRepository
        extends JpaRepository<StockLocation, Long> {

    Optional<StockLocation> findByCode(String code);

    boolean existsByCode(String code);

    List<StockLocation> findByActiveTrueOrderByNameAsc();
}
