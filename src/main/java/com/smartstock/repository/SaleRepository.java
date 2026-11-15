package com.smartstock.repository;
import com.smartstock.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface SaleRepository extends JpaRepository<Sale,Long>{
 boolean existsByProductId(Long productId);
 List<Sale> findAllByOrderBySoldAtDescIdDesc();
 List<Sale> findBySoldAtGreaterThanEqualAndSoldAtLessThan(LocalDateTime start,LocalDateTime end);
}
