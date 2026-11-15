package com.smartstock.repository;
import com.smartstock.model.Customer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface CustomerRepository extends JpaRepository<Customer,Long>{
 boolean existsByPhone(String phone);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from Customer c where c.id=:id") Optional<Customer> findLockedById(@Param("id") Long id);
}
