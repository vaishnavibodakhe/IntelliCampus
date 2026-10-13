package com.intellicampus.attendance.repository;

import com.intellicampus.attendance.model.AttendanceRecord;
import com.intellicampus.attendance.model.AttendanceSession;
import com.intellicampus.attendance.model.AttendanceStatus;
import com.intellicampus.user.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    List<AttendanceRecord> findBySession(AttendanceSession session);
    List<AttendanceRecord> findBySessionId(Long sessionId);
    Optional<AttendanceRecord> findBySessionAndStudent(AttendanceSession session, StudentProfile student);
    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, Long studentId);
    List<AttendanceRecord> findByStudentOrderByCreatedAtDesc(StudentProfile student);

    long countBySessionAndFinalStatus(AttendanceSession session, AttendanceStatus status);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId AND ar.finalStatus = 'PRESENT'")
    long countPresentByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId")
    long countTotalByStudentId(@Param("studentId") Long studentId);
}
