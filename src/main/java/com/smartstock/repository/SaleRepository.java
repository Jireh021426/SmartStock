package com.smartstock.repository;
import com.smartstock.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
public interface SaleRepository extends JpaRepository<Sale,Long>{
   boolean existsByProductId(Long productId);
  java.util.List<Sale> findByBusinessDate(LocalDate businessDate);
  java.util.List<Sale> findByBusinessDateGreaterThanEqualAndBusinessDateLessThan(LocalDate start, LocalDate end);
  java.util.List<Sale> findByBusinessDateIsNullAndSoldAtGreaterThanEqualAndSoldAtLessThan(LocalDateTime start, LocalDateTime end);
  boolean existsByDeliveryReference(String deliveryReference);
  java.util.List<Sale> findByDeliveryReferenceIsNotNullOrderBySoldAtDescIdDesc();
 List<Sale> findAllByOrderBySoldAtDescIdDesc();
 List<Sale> findBySoldAtGreaterThanEqualAndSoldAtLessThan(LocalDateTime start,LocalDateTime end);
}
