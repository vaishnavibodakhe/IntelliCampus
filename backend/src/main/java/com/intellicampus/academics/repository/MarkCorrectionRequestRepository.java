package com.intellicampus.academics.repository;

import com.intellicampus.academics.model.CorrectionStatus;
import com.intellicampus.academics.model.MarkCorrectionRequest;
import com.intellicampus.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarkCorrectionRequestRepository extends JpaRepository<MarkCorrectionRequest, Long> {
    List<MarkCorrectionRequest> findByStatusOrderByRequestedAtDesc(CorrectionStatus status);
    List<MarkCorrectionRequest> findByFacultyOrderByRequestedAtDesc(User faculty);
    List<MarkCorrectionRequest> findByMarkEntryId(Long markEntryId);
    long countByStatus(CorrectionStatus status);
}
