package com.intellicampus.leave.repository;

import com.intellicampus.leave.model.LeaveRequest;
import com.intellicampus.leave.model.LeaveStatus;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findByStudentOrderBySubmittedAtDesc(StudentProfile student);
    List<LeaveRequest> findByMentorOrderBySubmittedAtDesc(User mentor);
    List<LeaveRequest> findByMentorAndMentorStatus(User mentor, LeaveStatus mentorStatus);
    List<LeaveRequest> findByHodStatus(LeaveStatus hodStatus);
    List<LeaveRequest> findByFinalStatus(LeaveStatus finalStatus);

    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE lr.student.id = :studentId AND lr.finalStatus = 'APPROVED' AND lr.fromDate >= :since")
    long countApprovedDaysByStudentSince(@Param("studentId") Long studentId, @Param("since") LocalDate since);
}
