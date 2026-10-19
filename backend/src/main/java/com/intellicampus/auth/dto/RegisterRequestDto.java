package com.intellicampus.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterRequestDto {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 50, message = "Password must be at least 6 characters long")
    private String password;

    @NotBlank(message = "Roll number / PRN is required")
    @Size(min = 2, max = 30, message = "Roll number must be between 2 and 30 characters")
    private String rollNo;

    @NotBlank(message = "Department is required")
    private String department;

    @NotNull(message = "Academic year is required")
    private Integer year;

    public RegisterRequestDto() {}

    public RegisterRequestDto(String name, String email, String password, String rollNo, String department, Integer year) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.rollNo = rollNo;
        this.department = department;
        this.year = year;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
}
