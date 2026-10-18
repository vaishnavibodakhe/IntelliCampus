package com.intellicampus.user.dto;

import java.time.Instant;

public class CaptureTokenResponseDto {

    private String token;
    private Long studentId;
    private long expiresInSeconds;
    private Instant expiresAt;

    public CaptureTokenResponseDto() {}

    public CaptureTokenResponseDto(String token, Long studentId, long expiresInSeconds, Instant expiresAt) {
        this.token = token;
        this.studentId = studentId;
        this.expiresInSeconds = expiresInSeconds;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
