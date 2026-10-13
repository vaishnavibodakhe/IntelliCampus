package com.intellicampus.credential.repository;

import com.intellicampus.credential.model.DocumentStatus;
import com.intellicampus.credential.model.VerifiableDocument;
import com.intellicampus.user.model.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerifiableDocumentRepository extends JpaRepository<VerifiableDocument, Long> {
    Optional<VerifiableDocument> findByCode(String code);
    Optional<VerifiableDocument> findByFileHash(String fileHash);
    List<VerifiableDocument> findByStudentOrderByIssueDateDesc(StudentProfile student);
    List<VerifiableDocument> findByStatus(DocumentStatus status);
    boolean existsByCode(String code);
}
