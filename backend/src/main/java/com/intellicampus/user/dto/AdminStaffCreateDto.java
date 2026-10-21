package com.intellicampus.user.dto;

import com.intellicampus.user.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AdminStaffCreateDto {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email address is required")
    @Size(max = 120, message = "Email must not exceed 120 characters")
    private String email;

    @NotNull(message = "Role is required")
    private Role role;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Employee code is required")
    @Size(max = 40, message = "Employee code must not exceed 40 characters")
    private String employeeCode;

    public AdminStaffCreateDto() {}

    public AdminStaffCreateDto(String name, String email, Role role, String department, String employeeCode) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.employeeCode = employeeCode;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
}
