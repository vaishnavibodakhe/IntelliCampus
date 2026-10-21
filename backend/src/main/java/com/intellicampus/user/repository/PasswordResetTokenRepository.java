package com.intellicampus.user.repository;

import com.intellicampus.user.model.PasswordResetToken;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findByTokenHashAndUsedFalse(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    Optional<PasswordResetToken> findByResetTokenHashAndUsedFalse(String resetTokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true WHERE p.user = :user AND p.used = false")
    void invalidateAllActiveTokensForUser(@Param("user") User user);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(@Param("user") User user);

    long countByUserAndCreatedAtAfter(User user, LocalDateTime after);
}
