package com.intellicampus.user.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @com.fasterxml.jackson.annotation.JsonAlias({"username", "userCode"})
    private String identifier;
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    private Boolean rememberMe = false;

    public LoginRequest() {}

    public LoginRequest(String email, String password) {
        this.email = email;
        this.identifier = email;
        this.password = password;
    }

    public String getIdentifier() {
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        return email;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getEmail() {
        if (email != null && !email.isBlank()) {
            return email;
        }
        return identifier;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Boolean getRememberMe() { return rememberMe; }
    public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }
}
