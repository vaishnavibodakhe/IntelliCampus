package com.intellicampus.auth.service;

import com.intellicampus.user.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component("authJwtTokenProvider")
public class JwtTokenProvider {

    private final String secret;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;

    public JwtTokenProvider(
            @Value("${JWT_SECRET:${app.jwt.secret:}}") String secret,
            @Value("${app.jwt.access-expiration-minutes:15}") long accessExpirationMinutes,
            @Value("${app.jwt.refresh-expiration-days:7}") long refreshExpirationDays) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("JWT_SECRET environment variable is missing or shorter than 32 characters.");
        }
        this.secret = secret.trim();
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user, String rollNo) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpirationMinutes * 60 * 1000L);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("userCode", user.getUserCode())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("rollNo", rollNo != null ? rollNo : "")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationDays * 24 * 60 * 60 * 1000L);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long getUserIdFromToken(String token) {
        Number idNum = getClaims(token).get("userId", Number.class);
        return idNum != null ? idNum.longValue() : null;
    }

    public String getUserCodeFromToken(String token) {
        return getClaims(token).get("userCode", String.class);
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMinutes * 60;
    }
}
