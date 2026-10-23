package com.intellicampus.notice.repository;

import com.intellicampus.notice.model.Notice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findAllByOrderByPublishedAtDesc();

    @Query("SELECT DISTINCT n FROM Notice n " +
           "LEFT JOIN n.targetStudentIds sId " +
           "WHERE (sId = :userId) OR " +
           "(n.targetStudentIds IS EMPTY AND " +
           "(n.targetDepartment = 'All Departments' OR n.targetDepartment = 'All' OR :dept = 'All Departments' OR LOWER(n.targetDepartment) = LOWER(:dept)) AND " +
           "(n.targetYear = 'All Years' OR n.targetYear = 'All' OR :year = 'All Years' OR LOWER(n.targetYear) = LOWER(:year) " +
           " OR (LOWER(n.targetYear) = 'te' AND LOWER(:year) = 'year 3') " +
           " OR (LOWER(n.targetYear) = 'se' AND LOWER(:year) = 'year 2') " +
           " OR (LOWER(n.targetYear) = 'fe' AND LOWER(:year) = 'year 1') " +
           " OR (LOWER(n.targetYear) = 'be' AND LOWER(:year) = 'year 4') " +
           " OR (LOWER(n.targetYear) = 'year 3' AND LOWER(:year) = 'te') " +
           " OR (LOWER(n.targetYear) = 'year 2' AND LOWER(:year) = 'se') " +
           " OR (LOWER(n.targetYear) = 'year 1' AND LOWER(:year) = 'fe') " +
           " OR (LOWER(n.targetYear) = 'year 4' AND LOWER(:year) = 'be')) AND " +
           "(n.targetDivision = 'All Divisions' OR n.targetDivision = 'All' OR :division = 'All Divisions' OR LOWER(n.targetDivision) = LOWER(:division) " +
           " OR (LOWER(n.targetDivision) IN ('div a', 'division a') AND LOWER(:division) IN ('div a', 'division a')) " +
           " OR (LOWER(n.targetDivision) IN ('div b', 'division b') AND LOWER(:division) IN ('div b', 'division b')) " +
           " OR (LOWER(n.targetDivision) IN ('div c', 'division c') AND LOWER(:division) IN ('div c', 'division c')) " +
           ")) " +
           "ORDER BY n.publishedAt DESC")
    List<Notice> findTargetedNoticesForUser(
        @Param("userId") Long userId,
        @Param("dept") String dept,
        @Param("year") String year,
        @Param("division") String division
    );

    @Query("SELECT n FROM Notice n WHERE " +
           "(n.targetStudentIds IS EMPTY) AND " +
           "(n.targetDepartment = 'All Departments' OR n.targetDepartment = 'All' OR :dept = 'All Departments' OR LOWER(n.targetDepartment) = LOWER(:dept)) AND " +
           "(n.targetYear = 'All Years' OR n.targetYear = 'All' OR :year = 'All Years' OR LOWER(n.targetYear) = LOWER(:year) " +
           " OR (LOWER(n.targetYear) = 'te' AND LOWER(:year) = 'year 3') " +
           " OR (LOWER(n.targetYear) = 'se' AND LOWER(:year) = 'year 2') " +
           " OR (LOWER(n.targetYear) = 'fe' AND LOWER(:year) = 'year 1') " +
           " OR (LOWER(n.targetYear) = 'be' AND LOWER(:year) = 'year 4')) AND " +
           "(n.targetDivision = 'All Divisions' OR n.targetDivision = 'All' OR :division = 'All Divisions' OR LOWER(n.targetDivision) = LOWER(:division) " +
           " OR (LOWER(n.targetDivision) IN ('div a', 'division a') AND LOWER(:division) IN ('div a', 'division a')) " +
           " OR (LOWER(n.targetDivision) IN ('div b', 'division b') AND LOWER(:division) IN ('div b', 'division b')) " +
           " OR (LOWER(n.targetDivision) IN ('div c', 'division c') AND LOWER(:division) IN ('div c', 'division c')) " +
           ") " +
           "ORDER BY n.publishedAt DESC")
    List<Notice> findTargetedNotices(
        @Param("dept") String dept,
        @Param("year") String year,
        @Param("division") String division
    );
}
