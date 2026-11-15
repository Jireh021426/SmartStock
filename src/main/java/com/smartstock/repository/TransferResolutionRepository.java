
package com.smartstock.repository;

import com.smartstock.model.TransferResolution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransferResolutionRepository
        extends JpaRepository<TransferResolution, Long> {

    boolean existsByTransferId(Long transferId);

    Optional<TransferResolution> findByTransferId(Long transferId);

    List<TransferResolution> findAllByOrderByResolvedAtDescIdDesc();
}
