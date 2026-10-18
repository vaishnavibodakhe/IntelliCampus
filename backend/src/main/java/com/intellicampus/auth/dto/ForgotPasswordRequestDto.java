package com.intellicampus.auth.dto;

public class ForgotPasswordRequestDto {

    private String loginCodeOrEmail;
    private String loginCode;
    private String email;

    public ForgotPasswordRequestDto() {}

    public ForgotPasswordRequestDto(String loginCodeOrEmail) {
        this.loginCodeOrEmail = loginCodeOrEmail;
    }

    public String getIdentifier() {
        if (loginCodeOrEmail != null && !loginCodeOrEmail.isBlank()) {
            return loginCodeOrEmail.trim();
        }
        if (loginCode != null && !loginCode.isBlank()) {
            return loginCode.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return "";
    }

    public String getLoginCodeOrEmail() { return loginCodeOrEmail; }
    public void setLoginCodeOrEmail(String loginCodeOrEmail) { this.loginCodeOrEmail = loginCodeOrEmail; }

    public String getLoginCode() { return loginCode; }
    public void setLoginCode(String loginCode) { this.loginCode = loginCode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
