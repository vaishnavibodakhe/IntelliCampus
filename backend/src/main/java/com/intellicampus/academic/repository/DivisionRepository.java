package com.intellicampus.academic.repository;

import com.intellicampus.academic.model.Division;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DivisionRepository extends JpaRepository<Division, Long> {
    List<Division> findByDepartmentId(Long departmentId);
    List<Division> findByDepartmentIdAndYear(Long departmentId, Integer year);
    Optional<Division> findByNameAndDepartmentId(String name, Long departmentId);
}
