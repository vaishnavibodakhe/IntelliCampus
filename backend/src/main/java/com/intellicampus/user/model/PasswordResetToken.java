package com.intellicampus.user.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens", indexes = {
    @Index(name = "idx_reset_token_hash", columnList = "token_hash"),
    @Index(name = "idx_reset_token_user", columnList = "user_id")
})
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    @Column(nullable = false)
    private Boolean used = false;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public PasswordResetToken() {}

    public PasswordResetToken(User user, String tokenHash, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.used = false;
        this.attempts = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getResetTokenHash() { return resetTokenHash; }
    public void setResetTokenHash(String resetTokenHash) { this.resetTokenHash = resetTokenHash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getUsed() { return used; }
    public void setUsed(Boolean used) { this.used = used; }

    public Integer getAttempts() { return attempts != null ? attempts : 0; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public void incrementAttempts() {
        this.attempts = (this.attempts != null ? this.attempts : 0) + 1;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
