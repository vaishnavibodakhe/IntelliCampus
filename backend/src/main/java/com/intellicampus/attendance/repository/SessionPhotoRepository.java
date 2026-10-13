package com.intellicampus.attendance.repository;

import com.intellicampus.attendance.model.AttendanceSession;
import com.intellicampus.attendance.model.SessionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionPhotoRepository extends JpaRepository<SessionPhoto, Long> {
    List<SessionPhoto> findBySession(AttendanceSession session);
    List<SessionPhoto> findBySessionId(Long sessionId);
    Optional<SessionPhoto> findBySha256(String sha256);
    boolean existsBySessionIdAndSha256(Long sessionId, String sha256);
}
