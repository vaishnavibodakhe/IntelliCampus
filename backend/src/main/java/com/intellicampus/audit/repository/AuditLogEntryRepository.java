package com.intellicampus.audit.repository;

import com.intellicampus.audit.model.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {
    Optional<AuditLogEntry> findByEntryHash(String entryHash);
    List<AuditLogEntry> findAllByOrderByIdAsc();
    List<AuditLogEntry> findAllByOrderByCreatedAtAsc();
    Optional<AuditLogEntry> findTopByOrderByCreatedAtDesc();
    List<AuditLogEntry> findByEntityTypeAndEntityIdOrderByIdAsc(String entityType, String entityId);

    @Query("SELECT a FROM AuditLogEntry a ORDER BY a.id DESC LIMIT 1")
    Optional<AuditLogEntry> findLatestEntry();

    long count();
}

