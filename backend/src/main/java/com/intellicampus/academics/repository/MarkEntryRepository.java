package com.intellicampus.academics.repository;

import com.intellicampus.academic.model.Subject;
import com.intellicampus.academics.model.MarkEntry;
import com.intellicampus.academics.model.MarkStatus;
import com.intellicampus.user.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarkEntryRepository extends JpaRepository<MarkEntry, Long> {
    List<MarkEntry> findByStudentOrderByCreatedAtDesc(StudentProfile student);
    List<MarkEntry> findBySubjectOrderByCreatedAtDesc(Subject subject);
    List<MarkEntry> findByStudentAndStatus(StudentProfile student, MarkStatus status);
    List<MarkEntry> findBySubjectAndExam(Subject subject, String exam);
    Optional<MarkEntry> findByStudentAndSubjectAndExam(StudentProfile student, Subject subject, String exam);
}
