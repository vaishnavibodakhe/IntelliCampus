package com.intellicampus.user.repository;

import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {
    Optional<StudentProfile> findByUser(User user);
    Optional<StudentProfile> findByUserId(Long userId);
    Optional<StudentProfile> findByRollNo(String rollNo);
    boolean existsByRollNo(String rollNo);
    List<StudentProfile> findByMentor(User mentor);
    List<StudentProfile> findByDivisionId(Long divisionId);
    List<StudentProfile> findByFaceEnrolledTrue();
    void deleteByUser(User user);
}
