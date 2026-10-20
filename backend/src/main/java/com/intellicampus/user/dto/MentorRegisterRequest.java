package com.intellicampus.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;

public class MentorRegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    @JsonAlias({"name", "fullName"})
    private String fullName;

    @NotBlank(message = "Mentor ID is required")
    @Pattern(regexp = "^MEN-\\d{3,}$", message = "Mentor Employee ID must follow the pattern MEN-xxx (e.g. MEN-123)")
    @JsonAlias({"employeeId", "mentorId", "userCode"})
    private String mentorId;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
        message = "Password must be at least 8 characters with uppercase, lowercase, and a number"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    private String specialization;

    public MentorRegisterRequest() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getName() { return fullName; }
    public void setName(String name) {
        if (this.fullName == null || this.fullName.isBlank()) {
            this.fullName = name;
        }
    }

    public String getMentorId() { return mentorId; }
    public void setMentorId(String mentorId) { this.mentorId = mentorId; }

    public String getEmployeeId() { return mentorId; }
    public void setEmployeeId(String employeeId) {
        if (this.mentorId == null || this.mentorId.isBlank()) {
            this.mentorId = employeeId;
        }
    }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
