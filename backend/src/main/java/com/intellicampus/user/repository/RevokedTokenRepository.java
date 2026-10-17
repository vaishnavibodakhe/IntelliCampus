package com.intellicampus.user.repository;

import com.intellicampus.user.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM RevokedToken r WHERE r.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
}
