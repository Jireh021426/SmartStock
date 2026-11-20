package com.smartstock.repository;
import com.smartstock.model.CreditPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
public interface CreditPaymentRepository extends JpaRepository<CreditPayment,Long>{
 Optional<CreditPayment> findByRequestId(String requestId);
 List<CreditPayment> findByBusinessDate(LocalDate businessDate);
 List<CreditPayment> findByBusinessDateIsNullAndPaidAtGreaterThanEqualAndPaidAtLessThan(LocalDateTime start, LocalDateTime end);
 List<CreditPayment> findByCustomerCustomerTypeAndCashierIsNullOrderByPaidAtDescIdDesc(com.smartstock.model.CustomerType customerType);
 List<CreditPayment> findAllByOrderByPaidAtDescIdDesc();
 List<CreditPayment> findByPaidAtGreaterThanEqualAndPaidAtLessThan(LocalDateTime start,LocalDateTime end);
}
