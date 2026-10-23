package com.intellicampus.security;

import com.intellicampus.academic.model.Classroom;
import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.ClassroomRepository;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.academic.repository.SubjectRepository;
import com.intellicampus.ai.controller.PrincipalAssistantController;
import com.intellicampus.ai.dto.PrincipalAssistantQueryDto;
import com.intellicampus.ai.dto.PrincipalAssistantResponseDto;
import com.intellicampus.ai.service.PrincipalAssistantService;
import com.intellicampus.audit.repository.AuditLogEntryRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.credential.model.DocumentStatus;
import com.intellicampus.credential.model.VerifiableDocument;
import com.intellicampus.credential.repository.VerifiableDocumentRepository;
import com.intellicampus.notice.model.Notice;
import com.intellicampus.notice.repository.NoticeRepository;
import com.intellicampus.security.util.SecurityUtils;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PrincipalAssistantSecurityTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DivisionRepository divisionRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private VerifiableDocumentRepository verifiableDocumentRepository;

    @Mock
    private AuditLogEntryRepository auditLogEntryRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private AuditChainService auditChainService;

    @Mock
    private SecurityUtils securityUtils;

    private PrincipalAssistantService assistantService;
    private PrincipalAssistantController assistantController;

    private User principalUser;
    private User facultyUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        assistantService = new PrincipalAssistantService(
                userRepository,
                departmentRepository,
                divisionRepository,
                subjectRepository,
                classroomRepository,
                noticeRepository,
                verifiableDocumentRepository,
                auditLogEntryRepository,
                studentProfileRepository,
                auditChainService
        );

        assistantController = new PrincipalAssistantController(assistantService, securityUtils);

        principalUser = new User("PRI-001", Role.PRINCIPAL, "Dr. Principal", "principal@campus.edu", "$2a$10$supersecretprincipalhash", null);
        principalUser.setId(1L);

        facultyUser = new User("FAC-001", Role.FACULTY, "Prof. Ramanujan", "ramanujan@campus.edu", "$2a$10$supersecretfacultyhash", null);
        facultyUser.setId(2L);

        studentUser = new User("STU-001", Role.STUDENT, "Aarav Student", "aarav@campus.edu", "$2a$10$supersecretstudenthash", null);
        studentUser.setId(3L);
    }

    @Nested
    @DisplayName("1. RBAC & Principal-Only Access (403 for Non-Principal Roles)")
    class RbacAuthorizationTests {

        @Test
        @DisplayName("Assistant query returns 403 Forbidden for STUDENT role")
        void testStudentRoleForbidden() {
            when(securityUtils.getCurrentUser()).thenReturn(Optional.of(studentUser));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    assistantController.queryAssistant(new PrincipalAssistantQueryDto("How many staff members exist?")));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Principal role required"));
        }

        @Test
        @DisplayName("Assistant query returns 403 Forbidden for FACULTY role")
        void testFacultyRoleForbidden() {
            when(securityUtils.getCurrentUser()).thenReturn(Optional.of(facultyUser));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    assistantController.queryAssistant(new PrincipalAssistantQueryDto("How many staff members exist?")));

            assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
            assertTrue(ex.getReason().contains("Principal role required"));
        }

        @Test
        @DisplayName("Assistant query returns 401 Unauthorized when unauthenticated")
        void testUnauthenticatedReturns401() {
            when(securityUtils.getCurrentUser()).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    assistantController.queryAssistant(new PrincipalAssistantQueryDto("How many staff members exist?")));

            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }

        @Test
        @DisplayName("Assistant query succeeds for valid PRINCIPAL user")
        void testPrincipalAllowed() {
            when(securityUtils.getCurrentUser()).thenReturn(Optional.of(principalUser));
            when(userRepository.findAll()).thenReturn(List.of(principalUser));

            ResponseEntity<PrincipalAssistantResponseDto> response = assistantController.queryAssistant(
                    new PrincipalAssistantQueryDto("Summarize staff count"));

            assertNotNull(response);
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().isRefusal());
        }
    }

    @Nested
    @DisplayName("2. Sensitive Data Leakage Protection")
    class SensitiveDataSanitizationTests {

        @Test
        @DisplayName("Context and prompt NEVER include password hashes, tokens, OTPs, or student emails")
        void testSensitiveFieldsNeverAppearInContext() {
            Department csDept = new Department("CS", "Computer Science");
            User pendingStaff = new User("FAC-002", Role.FACULTY, "Dr. Turing", "turing@campus.edu", "$2a$10$hashedPasswordNeverLeak123", csDept);
            pendingStaff.setStatus(UserStatus.PENDING);

            User studentWithEmail = new User("STU-100", Role.STUDENT, "Aarav Kumar", "aarav.secret.email@campus.edu", "$2a$10$studentSecretHash", csDept);
            StudentProfile profile = new StudentProfile(studentWithEmail, "CS-101", null, null);
            profile.setAttendanceRate(88.5);

            when(userRepository.findAll()).thenReturn(List.of(principalUser, pendingStaff));
            when(studentProfileRepository.findAll()).thenReturn(List.of(profile));
            when(departmentRepository.findAll()).thenReturn(List.of(csDept));

            PrincipalAssistantService.InstitutionalSnapshot snapshot = assistantService.collectLiveInstitutionalSnapshot();
            String contextSummary = snapshot.toSummaryText();

            // Assert sensitive tokens and hashes are NOT present
            assertFalse(contextSummary.contains("$2a$10$"), "BCrypt hash must never be in prompt context");
            assertFalse(contextSummary.contains("hashedPasswordNeverLeak123"), "Password string must never be in prompt context");
            assertFalse(contextSummary.contains("aarav.secret.email@campus.edu"), "Student email must never be in prompt context");
            assertFalse(contextSummary.contains("token"), "Tokens must never be in prompt context");
            assertFalse(contextSummary.contains("otp"), "OTPs must never be in prompt context");

            // Verify safe summary data IS present
            assertTrue(contextSummary.contains("Dr. Turing"));
            assertTrue(contextSummary.contains("Computer Science"));
            assertTrue(contextSummary.contains("88.5%"));
        }
    }

    @Nested
    @DisplayName("3. Read-Only Action Refusal Defense")
    class ActionRefusalTests {

        @Test
        @DisplayName("Assistant refuses CREATE action request")
        void testRefuseCreateAction() {
            PrincipalAssistantResponseDto resp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("Create a new department called Robotics Engineering"),
                    principalUser
            );

            assertTrue(resp.isRefusal(), "Assistant must refuse creation request");
            assertTrue(resp.getAnswer().contains("cannot perform actions"));
            verify(departmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("Assistant refuses DELETE action request")
        void testRefuseDeleteAction() {
            PrincipalAssistantResponseDto resp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("Delete student with roll number CS-101"),
                    principalUser
            );

            assertTrue(resp.isRefusal(), "Assistant must refuse delete request");
            assertTrue(resp.getAnswer().contains("cannot perform actions"));
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Assistant refuses APPROVE action request")
        void testRefuseApproveAction() {
            PrincipalAssistantResponseDto resp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("Approve all pending faculty registrations now"),
                    principalUser
            );

            assertTrue(resp.isRefusal(), "Assistant must refuse approve request");
            assertTrue(resp.getAnswer().contains("cannot perform actions"));
        }

        @Test
        @DisplayName("Assistant refuses RESET PASSWORD action request")
        void testRefuseResetPasswordAction() {
            PrincipalAssistantResponseDto resp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("Reset password for user admin"),
                    principalUser
            );

            assertTrue(resp.isRefusal(), "Assistant must refuse password reset");
            assertTrue(resp.getAnswer().contains("cannot perform actions"));
        }
    }

    @Nested
    @DisplayName("4. Graceful Empty States & Out-of-Scope Handling")
    class EmptyStateAndOutOfScopeTests {

        @Test
        @DisplayName("Empty database returns clear, graceful answers without errors")
        void testEmptyDatabaseGracefulAnswers() {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());
            when(departmentRepository.findAll()).thenReturn(Collections.emptyList());
            when(divisionRepository.findAll()).thenReturn(Collections.emptyList());
            when(subjectRepository.findAll()).thenReturn(Collections.emptyList());
            when(classroomRepository.findAll()).thenReturn(Collections.emptyList());
            when(noticeRepository.findAll()).thenReturn(Collections.emptyList());
            when(verifiableDocumentRepository.findAll()).thenReturn(Collections.emptyList());
            when(auditLogEntryRepository.count()).thenReturn(0L);
            when(auditLogEntryRepository.findAllByOrderByIdAsc()).thenReturn(Collections.emptyList());
            when(studentProfileRepository.findAll()).thenReturn(Collections.emptyList());

            // 1. Notices
            PrincipalAssistantResponseDto noticesResp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("Are there any notices?"), principalUser);
            assertTrue(noticesResp.getAnswer().contains("No notices published yet"));

            // 2. Approvals
            PrincipalAssistantResponseDto approvalsResp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("How many approvals are pending?"), principalUser);
            assertTrue(approvalsResp.getAnswer().contains("0 pending staff registrations"));

            // 3. Credentials
            PrincipalAssistantResponseDto docsResp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("How many credentials or degrees have been issued?"), principalUser);
            assertTrue(docsResp.getAnswer().contains("No verifiable documents"));

            // 4. Academic Setup
            PrincipalAssistantResponseDto setupResp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("List academic departments"), principalUser);
            assertTrue(setupResp.getAnswer().contains("The academic hierarchy is currently empty"));
        }

        @Test
        @DisplayName("Out-of-scope query gracefully admits lack of knowledge")
        void testOutOfScopeRefusal() {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            PrincipalAssistantResponseDto resp = assistantService.queryAssistant(
                    new PrincipalAssistantQueryDto("What is the recipe for chocolate cake?"), principalUser);

            assertTrue(resp.isRefusal());
            assertTrue(resp.getAnswer().contains("I do not find information regarding this question in the live institutional database"));
        }
    }

    @Nested
    @DisplayName("5. Input Validation, Rate Limiting & Audit Logging")
    class InputValidationAndAuditTests {

        @Test
        @DisplayName("Question exceeding 500 characters throws 400 Bad Request")
        void testQuestionOver500CharsThrows400() {
            String longQuery = "a".repeat(501);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    assistantService.queryAssistant(new PrincipalAssistantQueryDto(longQuery), principalUser));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            assertTrue(ex.getReason().contains("exceeds maximum length"));
        }

        @Test
        @DisplayName("Blank question throws 400 Bad Request")
        void testBlankQuestionThrows400() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                    assistantService.queryAssistant(new PrincipalAssistantQueryDto("   "), principalUser));

            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("Audit chain records PRINCIPAL_ASSISTANT_QUERY event without sensitive content")
        void testAuditChainLogging() {
            when(userRepository.findAll()).thenReturn(Collections.emptyList());

            assistantService.queryAssistant(new PrincipalAssistantQueryDto("Show active notices"), principalUser);

            verify(auditChainService).recordEntry(
                    eq(1L),
                    eq("Dr. Principal"),
                    eq("PRINCIPAL_ASSISTANT_QUERY"),
                    eq("AI_ASSISTANT"),
                    eq("ASSISTANT"),
                    isNull(),
                    contains("queryLength"),
                    anyString()
            );
        }
    }
}
