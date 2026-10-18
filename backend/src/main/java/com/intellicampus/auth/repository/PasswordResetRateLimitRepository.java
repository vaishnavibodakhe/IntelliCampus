package com.intellicampus.auth.repository;

import com.intellicampus.auth.model.PasswordResetRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PasswordResetRateLimitRepository extends JpaRepository<PasswordResetRateLimit, Long> {
    long countByEmailAndRequestedAtAfter(String email, LocalDateTime after);
    long countByIpAddressAndRequestedAtAfter(String ipAddress, LocalDateTime after);
    java.util.List<PasswordResetRateLimit> findByEmailAndRequestedAtAfterOrderByRequestedAtAsc(String email, LocalDateTime after);
    java.util.List<PasswordResetRateLimit> findByIpAddressAndRequestedAtAfterOrderByRequestedAtAsc(String ipAddress, LocalDateTime after);
}
