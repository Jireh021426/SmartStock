
package com.smartstock.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StockLocationForm {

    @NotBlank(message = "Location code is required")
    @Size(max = 30, message = "Code must not exceed 30 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message = "Use letters, numbers, underscores or hyphens"
    )
    private String code;

    @NotBlank(message = "Location name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Select a location type")
    @Pattern(
            regexp = "STALL|STORAGE",
            message = "Location type must be STALL or STORAGE"
    )
    private String locationType = "STALL";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }
}
