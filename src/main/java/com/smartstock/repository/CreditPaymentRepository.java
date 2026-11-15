package com.smartstock.repository;
import com.smartstock.model.CreditPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface CreditPaymentRepository extends JpaRepository<CreditPayment,Long>{
 Optional<CreditPayment> findByRequestId(String requestId);
 List<CreditPayment> findAllByOrderByPaidAtDescIdDesc();
 List<CreditPayment> findByPaidAtGreaterThanEqualAndPaidAtLessThan(LocalDateTime start,LocalDateTime end);
}
