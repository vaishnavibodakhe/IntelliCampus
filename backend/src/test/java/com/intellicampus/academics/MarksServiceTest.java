package com.intellicampus.academics;

import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.SubjectRepository;
import com.intellicampus.academics.dto.*;
import com.intellicampus.academics.model.CorrectionStatus;
import com.intellicampus.academics.model.MarkCorrectionRequest;
import com.intellicampus.academics.model.MarkEntry;
import com.intellicampus.academics.model.MarkStatus;
import com.intellicampus.academics.repository.MarkCorrectionRequestRepository;
import com.intellicampus.academics.repository.MarkEntryRepository;
import com.intellicampus.academics.service.MarksService;
import com.intellicampus.academics.validation.MarkRangeValidator;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for MarksService covering the marks publishing lifecycle,
 * draft-vs-published access control, and HOD correction approvals with audit ledger records.
 * Compliant with IC-SRS-1.0 Section 8.3 & 8.7, and Test Case TC-05.
 */
@ExtendWith(MockitoExtension.class)
class MarksServiceTest {

    @Mock
    private MarkEntryRepository markEntryRepository;
    @Mock
    private MarkCorrectionRequestRepository correctionRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MarkRangeValidator markRangeValidator;
    @Mock
    private AuditChainService auditChainService;

    @InjectMocks
    private MarksService marksService;

    private User faculty;
    private User hod;
    private User studentUser;
    private StudentProfile student;
    private Subject subject;

    @BeforeEach
    void setUp() {
        faculty = new User("Prof. S. Patil", "patil@intellicampus.edu", "pass", Role.FACULTY, "FAC-102");
        ReflectionTestUtils.setField(faculty, "id", 10L);

        hod = new User("Dr. K. Sharma", "hod.cs@intellicampus.edu", "pass", Role.HOD, "HOD-001");
        ReflectionTestUtils.setField(hod, "id", 20L);

        studentUser = new User("Rohan Gupta", "rohan@intellicampus.edu", "pass", Role.STUDENT, "STU-205");
        ReflectionTestUtils.setField(studentUser, "id", 30L);

        student = new StudentProfile(studentUser, "2024-CS-088");
        ReflectionTestUtils.setField(student, "id", 100L);

        subject = new Subject("CS302", "Database Management Systems", 4);
        ReflectionTestUtils.setField(subject, "id", 50L);
    }

    @Nested
    @DisplayName("Marks Draft vs Published Access Control Tests")
    class DraftPublishVisibilityTests {

        @Test
        @DisplayName("Criterion 1: Student cannot view marks in DRAFT status until faculty publishes")
        void testStudentCannotViewDraftMarks() {
            MarkEntry publishedMark = new MarkEntry(student, subject, "Unit Test 1", 24.0, 30.0, MarkStatus.PUBLISHED, faculty);
            ReflectionTestUtils.setField(publishedMark, "id", 1L);

            MarkEntry draftMark = new MarkEntry(student, subject, "Mid-Term Examination", 38.0, 50.0, MarkStatus.DRAFT, faculty);
            ReflectionTestUtils.setField(draftMark, "id", 2L);

            when(studentProfileRepository.findById(100L)).thenReturn(Optional.of(student));
            when(markEntryRepository.findByStudentOrderByCreatedAtDesc(student))
                    .thenReturn(List.of(publishedMark, draftMark));

            // Student query: onlyPublished = true
            List<MarkResponseDto> studentView = marksService.getMarksForStudent(100L, true);

            assertEquals(1, studentView.size(), "Student should only see 1 mark entry");
            assertEquals("Unit Test 1", studentView.get(0).getExam());
            assertEquals(MarkStatus.PUBLISHED, studentView.get(0).getStatus());
            assertEquals(24.0, studentView.get(0).getMarks());

            // Faculty query: onlyPublished = false
            List<MarkResponseDto> facultyView = marksService.getMarksForStudent(100L, false);

            assertEquals(2, facultyView.size(), "Faculty should see both published and draft entries");
        }

        @Test
        @DisplayName("Should save marks in DRAFT status and validate mark boundary")
        void testSaveMarksDraft() {
            SaveMarkRequestDto req = new SaveMarkRequestDto();
            req.setStudentId(100L);
            req.setSubjectId(50L);
            req.setFacultyId(10L);
            req.setExam("Mid-Term Examination");
            req.setMarks(42.5);
            req.setMaxMarks(50.0);
            req.setStatus(MarkStatus.DRAFT);

            when(studentProfileRepository.findById(100L)).thenReturn(Optional.of(student));
            when(subjectRepository.findById(50L)).thenReturn(Optional.of(subject));
            when(userRepository.findById(10L)).thenReturn(Optional.of(faculty));
            when(markEntryRepository.findByStudentAndSubjectAndExam(student, subject, "Mid-Term Examination"))
                    .thenReturn(Optional.empty());

            when(markEntryRepository.save(any(MarkEntry.class))).thenAnswer(inv -> {
                MarkEntry m = inv.getArgument(0);
                ReflectionTestUtils.setField(m, "id", 55L);
                return m;
            });

            MarkResponseDto response = marksService.saveOrUpdateMark(req);

            assertNotNull(response);
            assertEquals(42.5, response.getMarks());
            assertEquals(50.0, response.getMaxMarks());
            assertEquals(MarkStatus.DRAFT, response.getStatus());

            verify(markRangeValidator).validateMarks(42.5, 50.0);
            verify(markEntryRepository).save(any(MarkEntry.class));
        }
    }

    @Nested
    @DisplayName("HOD Mark Correction Decision & Cryptographic Audit Tests")
    class HodCorrectionDecisionTests {

        @Test
        @DisplayName("Criterion 2: HOD approval applies mark correction and creates cryptographic audit entry")
        void testHodApprovalAppliesCorrectionAndCreatesAuditEntry() {
            MarkEntry originalMark = new MarkEntry(student, subject, "Mid-Term Examination", 18.0, 30.0, MarkStatus.PUBLISHED, faculty);
            ReflectionTestUtils.setField(originalMark, "id", 77L);

            MarkCorrectionRequest request = new MarkCorrectionRequest(
                    originalMark,
                    18.0,
                    23.0,
                    "Re-evaluation of question 3B after student verification",
                    faculty
            );
            ReflectionTestUtils.setField(request, "id", 201L);

            CorrectionDecisionDto decision = new CorrectionDecisionDto();
            decision.setApproved(true);
            decision.setHodId(20L);
            decision.setComment("Approved after paper re-check with department moderator");

            when(correctionRepository.findById(201L)).thenReturn(Optional.of(request));
            when(userRepository.findById(20L)).thenReturn(Optional.of(hod));
            when(markEntryRepository.save(originalMark)).thenReturn(originalMark);
            when(correctionRepository.save(request)).thenReturn(request);

            CorrectionResponseDto result = marksService.decideCorrection(201L, decision);

            assertNotNull(result);
            assertEquals(CorrectionStatus.APPROVED, result.getStatus());
            assertEquals(23.0, originalMark.getMarks(), "Target MarkEntry should be updated to new marks (23.0)");

            verify(markEntryRepository).save(originalMark);
            verify(correctionRepository).save(request);

            // Verify SHA-256 chained audit ledger entry creation
            verify(auditChainService).recordEntry(
                    eq(20L),
                    eq("Dr. K. Sharma"),
                    eq("MARK_CORRECTION_APPROVED"),
                    eq("MARK"),
                    eq("77"),
                    eq("{\"marks\":18.0}"),
                    eq("{\"marks\":23.0}"),
                    contains("HOD approved mark correction")
            );
        }

        @Test
        @DisplayName("HOD rejection preserves original marks and records rejection on audit chain")
        void testHodRejectionPreservesMarksAndRecordsAudit() {
            MarkEntry originalMark = new MarkEntry(student, subject, "Mid-Term Examination", 18.0, 30.0, MarkStatus.PUBLISHED, faculty);
            ReflectionTestUtils.setField(originalMark, "id", 77L);

            MarkCorrectionRequest request = new MarkCorrectionRequest(
                    originalMark,
                    18.0,
                    23.0,
                    "Re-evaluation request",
                    faculty
            );
            ReflectionTestUtils.setField(request, "id", 201L);

            CorrectionDecisionDto decision = new CorrectionDecisionDto();
            decision.setApproved(false);
            decision.setHodId(20L);
            decision.setComment("Rejected - original grading rubric correctly applied");

            when(correctionRepository.findById(201L)).thenReturn(Optional.of(request));
            when(userRepository.findById(20L)).thenReturn(Optional.of(hod));
            when(correctionRepository.save(request)).thenReturn(request);

            CorrectionResponseDto result = marksService.decideCorrection(201L, decision);

            assertEquals(CorrectionStatus.REJECTED, result.getStatus());
            assertEquals(18.0, originalMark.getMarks(), "Original marks should remain unchanged at 18.0");

            verify(markEntryRepository, never()).save(originalMark);
            verify(auditChainService).recordEntry(
                    eq(20L),
                    eq("Dr. K. Sharma"),
                    eq("MARK_CORRECTION_REJECTED"),
                    eq("MARK"),
                    eq("77"),
                    anyString(),
                    anyString(),
                    contains("Rejected")
            );
        }
    }
}
