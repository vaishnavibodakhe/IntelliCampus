package com.intellicampus.auth.repository;

import com.intellicampus.auth.model.PasswordResetToken;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    List<PasswordResetToken> findByUserAndUsedFalse(User user);

    List<PasswordResetToken> findByUser(User user);

    Optional<PasswordResetToken> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    Optional<PasswordResetToken> findByTokenHashAndUsedFalse(String tokenHash);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
