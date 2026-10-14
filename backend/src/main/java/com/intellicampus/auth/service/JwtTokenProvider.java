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
import java.util.Arrays;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final String secret;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:4c8a91f2b6e3d7a8c1f4e5a9b2d8c3e7f1a5b9c2d8e4f1a7b3c9d5e2f8a4b6c1}") String secret,
            @Value("${app.jwt.access-expiration-minutes:15}") long accessExpirationMinutes,
            @Value("${app.jwt.refresh-expiration-days:7}") long refreshExpirationDays) {
        this.secret = secret;
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            keyBytes = Arrays.copyOf(keyBytes, 32);
        }
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

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMinutes * 60;
    }
}
