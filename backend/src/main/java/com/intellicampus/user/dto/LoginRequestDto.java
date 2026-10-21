package com.intellicampus.user.dto;

public class LoginRequestDto {
    private String identifier; // Email OR Roll No
    private String password;

    public LoginRequestDto() {}

    public LoginRequestDto(String identifier, String password) {
        this.identifier = identifier;
        this.password = password;
    }

    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
