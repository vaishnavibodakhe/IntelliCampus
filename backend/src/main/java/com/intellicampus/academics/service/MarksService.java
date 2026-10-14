package com.intellicampus.academics.service;

import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.SubjectRepository;
import com.intellicampus.academics.dto.*;
import com.intellicampus.academics.model.CorrectionStatus;
import com.intellicampus.academics.model.MarkCorrectionRequest;
import com.intellicampus.academics.model.MarkEntry;
import com.intellicampus.academics.model.MarkStatus;
import com.intellicampus.academics.repository.MarkCorrectionRequestRepository;
import com.intellicampus.academics.repository.MarkEntryRepository;
import com.intellicampus.academics.validation.MarkRangeValidator;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.audit.service.AuditChainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MarksService {

    private final MarkEntryRepository markEntryRepository;
    private final MarkCorrectionRequestRepository correctionRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final MarkRangeValidator markRangeValidator;
    private final AuditChainService auditChainService;

    public MarksService(MarkEntryRepository markEntryRepository,
                        MarkCorrectionRequestRepository correctionRepository,
                        StudentProfileRepository studentProfileRepository,
                        SubjectRepository subjectRepository,
                        UserRepository userRepository,
                        MarkRangeValidator markRangeValidator,
                        AuditChainService auditChainService) {
        this.markEntryRepository = markEntryRepository;
        this.correctionRepository = correctionRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.subjectRepository = subjectRepository;
        this.userRepository = userRepository;
        this.markRangeValidator = markRangeValidator;
        this.auditChainService = auditChainService;
    }

    public MarkResponseDto saveOrUpdateMark(SaveMarkRequestDto req) {
        markRangeValidator.validateMarks(req.getMarks(), req.getMaxMarks());

        StudentProfile student = studentProfileRepository.findById(req.getStudentId())
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + req.getStudentId()));
        Subject subject = subjectRepository.findById(req.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + req.getSubjectId()));
        User faculty = null;
        if (req.getFacultyId() != null) {
            faculty = userRepository.findById(req.getFacultyId()).orElse(null);
        }

        MarkEntry entry = markEntryRepository.findByStudentAndSubjectAndExam(student, subject, req.getExam())
                .orElseGet(() -> new MarkEntry(student, subject, req.getExam(), req.getMarks(), req.getMaxMarks(), req.getStatus(), null));

        entry.setMarks(req.getMarks());
        entry.setMaxMarks(req.getMaxMarks());
        entry.setStatus(req.getStatus() != null ? req.getStatus() : MarkStatus.DRAFT);
        if (faculty != null) entry.setUpdatedBy(faculty);

        MarkEntry saved = markEntryRepository.save(entry);
        return mapMarkToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<MarkResponseDto> getMarksForStudent(Long studentId, boolean onlyPublished) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found: " + studentId));

        List<MarkEntry> marks = markEntryRepository.findByStudentOrderByCreatedAtDesc(student);
        return marks.stream()
                .filter(m -> !onlyPublished || m.getStatus() == MarkStatus.PUBLISHED)
                .map(this::mapMarkToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MarkResponseDto> getMarksForSubject(Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + subjectId));

        return markEntryRepository.findBySubjectOrderByCreatedAtDesc(subject).stream()
                .map(this::mapMarkToDto)
                .collect(Collectors.toList());
    }

    public CorrectionResponseDto submitCorrectionRequest(SubmitCorrectionRequestDto req) {
        MarkEntry mark = markEntryRepository.findById(req.getMarkId())
                .orElseThrow(() -> new IllegalArgumentException("Mark record not found: " + req.getMarkId()));

        markRangeValidator.validateCorrection(req.getNewMarks(), mark.getMaxMarks(), req.getReason());

        User faculty = userRepository.findById(req.getFacultyId())
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found: " + req.getFacultyId()));

        MarkCorrectionRequest correction = new MarkCorrectionRequest(
                mark,
                mark.getMarks(),
                req.getNewMarks(),
                req.getReason(),
                faculty
        );

        MarkCorrectionRequest saved = correctionRepository.save(correction);
        return mapCorrectionToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponseDto> getPendingCorrections() {
        return correctionRepository.findByStatusOrderByRequestedAtDesc(CorrectionStatus.PENDING_HOD).stream()
                .map(this::mapCorrectionToDto)
                .collect(Collectors.toList());
    }

    public CorrectionResponseDto decideCorrection(Long correctionId, CorrectionDecisionDto decision) {
        MarkCorrectionRequest correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> new IllegalArgumentException("Correction request not found: " + correctionId));

        if (decision.getHodId() != null) {
            userRepository.findById(decision.getHodId()).ifPresent(correction::setHod);
        }

        correction.setDecisionComment(decision.getComment());
        correction.setDecidedAt(LocalDateTime.now());

        if (decision.isApproved()) {
            correction.setStatus(CorrectionStatus.APPROVED);
            // Apply new marks to the target MarkEntry record
            MarkEntry mark = correction.getMarkEntry();
            Double oldVal = mark.getMarks();
            mark.setMarks(correction.getNewMarks());
            markEntryRepository.save(mark);

            // Record HOD approval on cryptographic audit chain
            if (auditChainService != null) {
                String hodName = (correction.getHod() != null) ? correction.getHod().getName() : "HOD";
                auditChainService.recordEntry(
                        decision.getHodId() != null ? decision.getHodId() : 1L,
                        hodName,
                        "MARK_CORRECTION_APPROVED",
                        "MARK",
                        String.valueOf(mark.getId()),
                        String.format("{\"marks\":%.1f}", oldVal),
                        String.format("{\"marks\":%.1f}", correction.getNewMarks()),
                        "HOD approved mark correction: " + (decision.getComment() != null ? decision.getComment() : correction.getReason())
                );
            }
        } else {
            correction.setStatus(CorrectionStatus.REJECTED);
            if (auditChainService != null) {
                String hodName = (correction.getHod() != null) ? correction.getHod().getName() : "HOD";
                auditChainService.recordEntry(
                        decision.getHodId() != null ? decision.getHodId() : 1L,
                        hodName,
                        "MARK_CORRECTION_REJECTED",
                        "MARK",
                        String.valueOf(correction.getMarkEntry().getId()),
                        String.format("{\"marks\":%.1f}", correction.getOldMarks()),
                        String.format("{\"marks\":%.1f}", correction.getOldMarks()),
                        "HOD rejected mark correction: " + (decision.getComment() != null ? decision.getComment() : "Rejected")
                );
            }
        }

        MarkCorrectionRequest saved = correctionRepository.save(correction);
        return mapCorrectionToDto(saved);
    }

    private MarkResponseDto mapMarkToDto(MarkEntry m) {
        MarkResponseDto dto = new MarkResponseDto();
        dto.setId(m.getId());
        dto.setStudentId(m.getStudent().getId());
        dto.setStudentName(m.getStudent().getUser().getName());
        dto.setRollNo(m.getStudent().getRollNo());
        dto.setSubjectId(m.getSubject().getId());
        dto.setSubjectCode(m.getSubject().getCode());
        dto.setSubjectName(m.getSubject().getName());
        dto.setExam(m.getExam());
        dto.setMarks(m.getMarks());
        dto.setMaxMarks(m.getMaxMarks());
        dto.setStatus(m.getStatus());
        dto.setUpdatedBy(m.getUpdatedBy() != null ? m.getUpdatedBy().getName() : "System");
        dto.setUpdatedAt(m.getUpdatedAt());
        return dto;
    }

    private CorrectionResponseDto mapCorrectionToDto(MarkCorrectionRequest c) {
        CorrectionResponseDto dto = new CorrectionResponseDto();
        dto.setId(c.getId());
        dto.setMarkId(c.getMarkEntry().getId());
        dto.setStudentName(c.getMarkEntry().getStudent().getUser().getName());
        dto.setRollNo(c.getMarkEntry().getStudent().getRollNo());
        dto.setSubjectCode(c.getMarkEntry().getSubject().getCode());
        dto.setExam(c.getMarkEntry().getExam());
        dto.setOldMarks(c.getOldMarks());
        dto.setNewMarks(c.getNewMarks());
        dto.setReason(c.getReason());
        dto.setFacultyName(c.getFaculty().getName());
        dto.setStatus(c.getStatus());
        dto.setRequestedAt(c.getRequestedAt());
        dto.setDecisionComment(c.getDecisionComment());
        return dto;
    }
}
