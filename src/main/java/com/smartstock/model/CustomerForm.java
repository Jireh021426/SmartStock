package com.smartstock.model;
import jakarta.validation.constraints.*;
public class CustomerForm {
 @NotBlank @Size(max=150) private String name;
 @NotBlank @Size(max=80) private String phone;
 public String getName(){return name;}public void setName(String x){name=x;}public String getPhone(){return phone;}public void setPhone(String x){phone=x;}
}
