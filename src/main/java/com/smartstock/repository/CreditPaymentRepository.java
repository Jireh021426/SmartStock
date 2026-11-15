package com.smartstock.repository;
import com.smartstock.model.CreditPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface CreditPaymentRepository extends JpaRepository<CreditPayment,Long>{
 List<CreditPayment> findAllByOrderByPaidAtDescIdDesc();
 List<CreditPayment> findByPaidAtGreaterThanEqualAndPaidAtLessThan(LocalDateTime start,LocalDateTime end);
}
