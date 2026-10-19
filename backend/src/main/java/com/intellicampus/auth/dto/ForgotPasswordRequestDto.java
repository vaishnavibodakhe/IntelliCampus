package com.intellicampus.auth.dto;

import com.intellicampus.user.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ForgotPasswordRequestDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    private Role role;

    public ForgotPasswordRequestDto() {}

    public ForgotPasswordRequestDto(String email) {
        this.email = email;
    }

    public ForgotPasswordRequestDto(String email, Role role) {
        this.email = email;
        this.role = role;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
