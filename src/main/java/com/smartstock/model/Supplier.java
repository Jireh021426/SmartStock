
package com.smartstock.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
    name = "supplier",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_supplier_code",
            columnNames = "code"
        )
    }
)
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String contactNumber;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Supplier() {
    }

    public Supplier(
            String code,
            String name,
            String contactNumber,
            String address) {

        update(code, name, contactNumber, address);
        this.createdAt = LocalDateTime.now();
    }

    public void update(
            String code,
            String name,
            String contactNumber,
            String address) {

        if (code == null || name == null) {
            throw new IllegalArgumentException(
                    "Supplier code and name are required");
        }

        String cleanCode =
                code.trim().toUpperCase(Locale.ROOT);

        String cleanName = name.trim();

        if (cleanCode.isBlank()
                || cleanCode.length() > 40
                || !cleanCode.matches("[A-Z0-9_-]+")) {

            throw new IllegalArgumentException(
                    "Invalid supplier code");
        }

        if (cleanName.isBlank()
                || cleanName.length() > 150) {

            throw new IllegalArgumentException(
                    "Supplier name must contain 1-150 characters");
        }

        String cleanContact = optionalText(
                contactNumber, 50, "Contact number");

        String cleanAddress = optionalText(
                address, 255, "Address");

        this.code = cleanCode;
        this.name = cleanName;
        this.contactNumber = cleanContact;
        this.address = cleanAddress;
    }

    private static String optionalText(
            String value,
            int maxLength,
            String fieldName) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String cleaned = value.trim();

        if (cleaned.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " is too long");
        }

        return cleaned;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
