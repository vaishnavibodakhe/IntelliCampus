package com.intellicampus.academic.repository;

import com.intellicampus.academic.model.Subject;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    Optional<Subject> findByCode(String code);
    List<Subject> findByFaculty(User faculty);
    List<Subject> findByDepartmentId(Long departmentId);
}
