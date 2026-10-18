package com.intellicampus.auth.repository;

import com.intellicampus.auth.model.PasswordResetToken;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHashAndUsedFalse(String tokenHash);

    Optional<PasswordResetToken> findFirstByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.used = true WHERE t.user = :user AND t.used = false")
    void invalidateAllForUser(@Param("user") User user);
}
