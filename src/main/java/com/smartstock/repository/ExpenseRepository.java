package com.smartstock.repository;
import com.smartstock.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
public interface ExpenseRepository extends JpaRepository<Expense,Long>{
 List<Expense> findByIncurredAtGreaterThanEqualAndIncurredAtLessThanOrderByIncurredAtDesc(LocalDateTime start,LocalDateTime end);
}