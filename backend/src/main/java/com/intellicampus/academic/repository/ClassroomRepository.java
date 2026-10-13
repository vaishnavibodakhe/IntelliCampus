package com.intellicampus.academic.repository;

import com.intellicampus.academic.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {
    Optional<Classroom> findByName(String name);
    List<Classroom> findByDepartmentId(Long departmentId);
}
