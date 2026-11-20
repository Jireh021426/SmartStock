
package com.smartstock.repository;

import com.smartstock.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository
        extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Supplier> findAllByOrderByNameAscIdAsc();

    List<Supplier> findByActiveTrueOrderByNameAscIdAsc();
}
