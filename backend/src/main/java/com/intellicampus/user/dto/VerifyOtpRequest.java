package com.intellicampus.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerifyOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "6-digit OTP code is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
    @JsonAlias({"otp", "code", "token"})
    private String otp;

    private String role;

    public VerifyOtpRequest() {}

    public VerifyOtpRequest(String email, String otp) {
        this.email = email;
        this.otp = otp;
    }

    public VerifyOtpRequest(String email, String otp, String role) {
        this.email = email;
        this.otp = otp;
        this.role = role;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
