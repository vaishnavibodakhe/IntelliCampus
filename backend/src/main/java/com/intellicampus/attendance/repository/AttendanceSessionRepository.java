package com.intellicampus.attendance.repository;

import com.intellicampus.attendance.model.AttendanceSession;
import com.intellicampus.attendance.model.SessionStatus;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
