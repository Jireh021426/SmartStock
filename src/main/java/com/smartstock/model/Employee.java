
package com.smartstock.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "employee",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_employee_username",
            columnNames = "username"
        )
    }
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private StockLocation assignedLocation;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Employee() {
    }

    public Employee(
            String fullName,
            String username,
            String passwordHash,
            EmployeeRole role,
            StockLocation assignedLocation) {

        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException(
                "Employee name is required."
            );
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                "Username is required."
            );
        }

        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                "Password hash is required."
            );
        }

        if (role == null) {
            throw new IllegalArgumentException(
                "Employee role is required."
            );
        }

        this.fullName = fullName.trim();
        this.username = username.trim();
        this.passwordHash = passwordHash;
        this.role = role;
        this.assignedLocation = assignedLocation;
        this.active = true;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public EmployeeRole getRole() {
        return role;
    }

    public StockLocation getAssignedLocation() {
        return assignedLocation;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void changePassword(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException(
                "Password hash is required."
            );
        }
        this.passwordHash = passwordHash;
    }

    public void changeRole(EmployeeRole role) {
        if (role == null) {
            throw new IllegalArgumentException(
                "Employee role is required."
            );
        }
        this.role = role;
    }

    public void assignLocation(StockLocation location) {
        this.assignedLocation = location;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
