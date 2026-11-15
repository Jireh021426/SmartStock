
package com.smartstock.model;

import jakarta.persistence.*;

import java.util.Locale;

@Entity
@Table(
    name = "stock_location",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_stock_location_code",
            columnNames = "code"
        )
    }
)
public class StockLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String locationType;

    @Column(nullable = false)
    private boolean active = true;

    protected StockLocation() {
    }

    public StockLocation(
            String code,
            String name,
            String locationType) {

        update(code, name, locationType);
    }

    public void update(
            String code,
            String name,
            String locationType) {

        if (code == null ||
                name == null ||
                locationType == null) {

            throw new IllegalArgumentException(
                "Location code, name and type are required"
            );
        }

        String cleanCode =
                code.trim().toUpperCase(Locale.ROOT);

        String cleanName = name.trim();

        String cleanType =
                locationType.trim().toUpperCase(Locale.ROOT);

        if (cleanCode.isBlank() ||
                cleanCode.length() > 30 ||
                !cleanCode.matches("[A-Z0-9_-]+")) {

            throw new IllegalArgumentException(
                "Location code must contain 1-30 letters, numbers, underscores or hyphens"
            );
        }

        if (cleanName.isBlank() ||
                cleanName.length() > 100) {

            throw new IllegalArgumentException(
                "Location name must contain 1-100 characters"
            );
        }

        if (cleanType.isBlank() ||
                cleanType.length() > 30 ||
                !cleanType.matches("[A-Z0-9_]+")) {

            throw new IllegalArgumentException(
                "Invalid location type"
            );
        }

        this.code = cleanCode;
        this.name = cleanName;
        this.locationType = cleanType;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
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

    public String getLocationType() {
        return locationType;
    }

    public boolean isActive() {
        return active;
    }
}
