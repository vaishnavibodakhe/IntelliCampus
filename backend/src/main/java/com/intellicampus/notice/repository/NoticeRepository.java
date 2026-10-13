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

    @Query("SELECT n FROM Notice n WHERE " +
           "(n.targetDepartment = 'All Departments' OR n.targetDepartment = :dept) AND " +
           "(n.targetYear = 'All Years' OR n.targetYear = :year) AND " +
           "(n.targetDivision = 'All Divisions' OR n.targetDivision = :division) " +
           "ORDER BY n.publishedAt DESC")
    List<Notice> findTargetedNotices(
        @Param("dept") String dept,
        @Param("year") String year,
        @Param("division") String division
    );
}
