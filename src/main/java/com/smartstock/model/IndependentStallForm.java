package com.smartstock.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class IndependentStallForm {
    @NotBlank(message = "Stall name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Contact / account identifier is required")
    @Size(max = 255)
    private String phone;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
