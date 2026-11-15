
package com.smartstock.repository;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository
        extends JpaRepository<Employee, Long> {

    @EntityGraph(attributePaths = {"assignedLocation"})
    Optional<Employee> findByUsername(String username);

    // Lock the cashier row for financial transactions.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"assignedLocation"})
    @Query("""
            SELECT e
            FROM Employee e
            WHERE e.username = :username
            """)
    Optional<Employee> findLockedByUsername(
            @Param("username") String username
    );

    boolean existsByUsername(String username);

    List<Employee> findByActiveTrueOrderByFullNameAsc();

    List<Employee> findByRole(EmployeeRole role);

    List<Employee> findByAssignedLocationId(Long locationId);

    @Query("""
            SELECT DISTINCT e
            FROM Employee e
            LEFT JOIN FETCH e.assignedLocation
            ORDER BY e.fullName ASC
            """)
    List<Employee> findAllWithAssignedLocation();
}
