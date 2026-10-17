package com.intellicampus.user.dto;

import java.util.Map;

public class AuthResponseDto {
    private String token;
    private String tokenType = "Bearer";
    private int expiresInMinutes;
    private String message;
    private Map<String, Object> user;
    private Map<String, Object> student;

    public AuthResponseDto() {}

    public AuthResponseDto(String token, int expiresInMinutes, String message, Map<String, Object> user, Map<String, Object> student) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresInMinutes = expiresInMinutes;
        this.message = message;
        this.user = user;
        this.student = student;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public int getExpiresInMinutes() { return expiresInMinutes; }
    public void setExpiresInMinutes(int expiresInMinutes) { this.expiresInMinutes = expiresInMinutes; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getUser() { return user; }
    public void setUser(Map<String, Object> user) { this.user = user; }

    public Map<String, Object> getStudent() { return student; }
    public void setStudent(Map<String, Object> student) { this.student = student; }
}
