package com.intellicampus.auth.dto;

public class ResetPasswordRequestDto {

    private String loginCode;
    private String email;
    private String code;
    private String resetCode;
    private String newPassword;
    private String confirmPassword;

    public ResetPasswordRequestDto() {}

    public ResetPasswordRequestDto(String code, String newPassword, String confirmPassword) {
        this.code = code;
        this.resetCode = code;
        this.newPassword = newPassword;
        this.confirmPassword = confirmPassword;
    }

    public String getCode() {
        if (code != null && !code.isBlank()) return code.trim();
        if (resetCode != null && !resetCode.isBlank()) return resetCode.trim();
        return "";
    }

    public void setCode(String code) {
        this.code = code;
        if (this.resetCode == null) this.resetCode = code;
    }

    public String getResetCode() {
        if (resetCode != null && !resetCode.isBlank()) return resetCode.trim();
        if (code != null && !code.isBlank()) return code.trim();
        return "";
    }

    public void setResetCode(String resetCode) {
        this.resetCode = resetCode;
        if (this.code == null) this.code = resetCode;
    }

    public String getLoginCode() { return loginCode; }
    public void setLoginCode(String loginCode) { this.loginCode = loginCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
