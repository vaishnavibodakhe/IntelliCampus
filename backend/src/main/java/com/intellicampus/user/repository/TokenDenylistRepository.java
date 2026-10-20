package com.intellicampus.user.repository;

import com.intellicampus.user.model.TokenDenylist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokenDenylistRepository extends JpaRepository<TokenDenylist, Long> {
    boolean existsByToken(String token);
    Optional<TokenDenylist> findByToken(String token);
    void deleteByExpiresAtBefore(LocalDateTime now);
    void deleteByUserId(Long userId);
}
