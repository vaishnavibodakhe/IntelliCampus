package com.intellicampus.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequestDto {

    private String email;

    private String otp;

    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

    private String confirmPassword;

    public ResetPasswordRequestDto() {}

    public ResetPasswordRequestDto(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public ResetPasswordRequestDto(String email, String otp, String newPassword, String confirmPassword) {
        this.email = email;
        this.otp = otp;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getOtpOrToken() {
        if (otp != null && !otp.trim().isEmpty()) {
            return otp.trim();
        }
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }
        return null;
    }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
