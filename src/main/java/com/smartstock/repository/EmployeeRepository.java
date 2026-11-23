
package com.smartstock.repository;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // Admin sale entry locks the responsible cashier, preserving
    // serialization with cashier remittances and normal cashier sales.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"assignedLocation"})
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findLockedById(@Param("id") Long id);

    boolean existsByUsername(String username);

    // Paginated employee view requires the assigned location outside the transaction.
    @Override
    @EntityGraph(attributePaths = {"assignedLocation"})
    Page<Employee> findAll(Pageable pageable);

    // Case-insensitive name/username search. To-one location is fetched with each page.
    @EntityGraph(attributePaths = {"assignedLocation"})
    @Query("""
            SELECT e FROM Employee e
            WHERE LOWER(e.fullName) LIKE CONCAT('%', :term, '%')
               OR LOWER(e.username) LIKE CONCAT('%', :term, '%')
            """)
    Page<Employee> searchByNameOrUsername(@Param("term") String term, Pageable pageable);

    // Include enum roles when the term matches any role label, e.g., "cash" => CASHIER.
    @EntityGraph(attributePaths = {"assignedLocation"})
    @Query("""
            SELECT e FROM Employee e
            WHERE LOWER(e.fullName) LIKE CONCAT('%', :term, '%')
               OR LOWER(e.username) LIKE CONCAT('%', :term, '%')
               OR e.role IN :roles
            """)
    Page<Employee> searchByNameUsernameOrRole(
            @Param("term") String term,
            @Param("roles") Collection<EmployeeRole> roles,
            Pageable pageable);

    List<Employee> findByActiveTrueOrderByFullNameAsc();

    @EntityGraph(attributePaths = {"assignedLocation"})
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
