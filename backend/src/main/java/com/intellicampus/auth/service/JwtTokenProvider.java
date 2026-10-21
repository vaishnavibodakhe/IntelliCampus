package com.intellicampus.auth.service;

import com.intellicampus.user.model.User;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consolidated Adapter delegating to the unified security JwtTokenProvider.
 */
@Component("authJwtTokenProvider")
public class JwtTokenProvider {

    private final com.intellicampus.security.jwt.JwtTokenProvider delegate;

    public JwtTokenProvider(
            @Autowired(required = false) com.intellicampus.security.jwt.JwtTokenProvider delegate,
            @Value("${JWT_SECRET:${app.jwt.secret:}}") String secret,
            @Value("${app.jwt.access-expiration-minutes:15}") long accessExpirationMinutes,
            @Value("${app.jwt.refresh-expiration-days:7}") long refreshExpirationDays) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("JWT_SECRET environment variable is missing or shorter than 32 characters.");
        }
        this.delegate = delegate != null ? delegate : new com.intellicampus.security.jwt.JwtTokenProvider(secret, accessExpirationMinutes);
    }

    public String generateAccessToken(User user, String rollNo) {
        return delegate.generateAccessToken(user, rollNo);
    }

    public String generateRefreshToken(User user) {
        return delegate.generateRefreshToken(user);
    }

    public boolean validateToken(String token) {
        return delegate.validateToken(token);
    }

    public Claims getClaims(String token) {
        return delegate.getClaims(token);
    }

    public String getEmailFromToken(String token) {
        return delegate.getEmailFromToken(token);
    }

    public String getRoleFromToken(String token) {
        return delegate.getRoleFromToken(token);
    }

    public Long getUserIdFromToken(String token) {
        return delegate.getUserIdFromToken(token);
    }

    public long getAccessExpirationSeconds() {
        return delegate.getAccessExpirationSeconds();
    }
}
