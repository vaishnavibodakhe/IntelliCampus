package com.intellicampus.attendance.repository;

import com.intellicampus.attendance.model.AttendanceSession;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    List<AttendanceSession> findByFacultyOrderByStartedAtDesc(User faculty);
    List<AttendanceSession> findBySubjectIdOrderByStartedAtDesc(Long subjectId);
    List<AttendanceSession> findByDivisionIdOrderByStartedAtDesc(Long divisionId);
    Optional<AttendanceSession> findFirstByFacultyAndStatus(User faculty, SessionStatus status);
    Optional<AttendanceSession> findFirstByDivisionIdAndStatus(Long divisionId, SessionStatus status);
    List<AttendanceSession> findByStatus(SessionStatus status);

    @Modifying
    @Query("UPDATE AttendanceSession s SET s.photoCount = COALESCE(s.photoCount, 0) + 1 WHERE s.id = :id AND COALESCE(s.photoCount, 0) < 5")
    int incrementPhotoCountIfUnderLimit(@Param("id") Long id);
}
