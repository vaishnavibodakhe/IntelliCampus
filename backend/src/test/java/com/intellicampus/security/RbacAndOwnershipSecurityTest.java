package com.intellicampus.security;

import com.intellicampus.academic.controller.AcademicSetupController;
import com.intellicampus.academic.service.AcademicSetupService;
import com.intellicampus.academics.controller.MarksController;
import com.intellicampus.academics.dto.CorrectionDecisionDto;
import com.intellicampus.academics.dto.MarkResponseDto;
import com.intellicampus.academics.dto.SubmitCorrectionRequestDto;
import com.intellicampus.academics.model.MarkStatus;
import com.intellicampus.academics.service.MarksService;
import com.intellicampus.attendance.controller.AttendanceController;
import com.intellicampus.attendance.service.AttendanceService;
import com.intellicampus.audit.controller.AuditController;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.credential.controller.CredentialController;
import com.intellicampus.credential.service.CredentialService;
import com.intellicampus.leave.controller.LeaveController;
import com.intellicampus.leave.dto.LeaveDecisionDto;
import com.intellicampus.leave.dto.LeaveResponseDto;
import com.intellicampus.leave.dto.SubmitLeaveRequestDto;
import com.intellicampus.leave.model.LeaveStatus;
import com.intellicampus.leave.service.LeaveService;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.user.controller.UserController;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        UserController.class,
        AuditController.class,
        MarksController.class,
        LeaveController.class,
        AttendanceController.class,
        CredentialController.class,
        AcademicSetupController.class
})
@Import(SecurityConfig.class)
public class RbacAndOwnershipSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AuditChainService auditChainService;

    @MockBean
    private MarksService marksService;

    @MockBean
    private LeaveService leaveService;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private CredentialService credentialService;

    @MockBean
    private AcademicSetupService academicSetupService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StudentProfileRepository studentProfileRepository;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            jakarta.servlet.ServletRequest request = invocation.getArgument(0);
            jakarta.servlet.ServletResponse response = invocation.getArgument(1);
            jakarta.servlet.FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Nested
    @DisplayName("1. Role-Based Access Control (RBAC) Default-Deny Tests")
    class RbacDefaultDenyTests {

        @Test
        @DisplayName("Anonymous access to /api/users returns 401 Unauthorized")
        void testAnonymousCannotAccessUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Anonymous access to /api/audit/chain returns 401 Unauthorized")
        void testAnonymousCannotAccessAudit() throws Exception {
            mockMvc.perform(get("/api/audit/chain"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "student@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("STUDENT role accessing /api/users returns 403 Forbidden")
        void testStudentCannotAccessUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "faculty@intellicampus.edu", roles = {"FACULTY"})
        @DisplayName("FACULTY role accessing /api/users returns 403 Forbidden")
        void testFacultyCannotAccessUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "faculty@intellicampus.edu", roles = {"FACULTY"})
        @DisplayName("FACULTY role accessing /api/audit/chain returns 403 Forbidden")
        void testFacultyCannotAccessAuditChain() throws Exception {
            mockMvc.perform(get("/api/audit/chain"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "mentor@intellicampus.edu", roles = {"MENTOR"})
        @DisplayName("MENTOR role accessing /api/audit/chain returns 403 Forbidden")
        void testMentorCannotAccessAuditChain() throws Exception {
            mockMvc.perform(get("/api/audit/chain"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin@intellicampus.edu", roles = {"ADMIN"})
        @DisplayName("ADMIN role accessing /api/users returns 200 OK")
        void testAdminCanAccessUsers() throws Exception {
            when(userService.getAllUsers(any(), any())).thenReturn(Collections.emptyList());
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "admin@intellicampus.edu", roles = {"ADMIN"})
        @DisplayName("ADMIN role accessing /api/audit/chain returns 200 OK")
        void testAdminCanAccessAuditChain() throws Exception {
            when(auditChainService.getChainEntries()).thenReturn(Collections.emptyList());
            mockMvc.perform(get("/api/audit/chain"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "hod.cs@intellicampus.edu", roles = {"HOD"})
        @DisplayName("HOD role accessing /api/marks/corrections/pending returns 200 OK")
        void testHodCanAccessPendingCorrections() throws Exception {
            when(marksService.getPendingCorrections()).thenReturn(Collections.emptyList());
            mockMvc.perform(get("/api/marks/corrections/pending"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "student@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("STUDENT role accessing /api/marks/corrections/pending returns 403 Forbidden")
        void testStudentCannotAccessPendingCorrections() throws Exception {
            mockMvc.perform(get("/api/marks/corrections/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "faculty@intellicampus.edu", roles = {"FACULTY"})
        @DisplayName("FACULTY role accessing /api/marks/corrections/pending returns 403 Forbidden")
        void testFacultyCannotAccessPendingCorrections() throws Exception {
            mockMvc.perform(get("/api/marks/corrections/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "hod.cs@intellicampus.edu", roles = {"HOD"})
        @DisplayName("HOD role accessing /api/leaves/hod/pending returns 200 OK")
        void testHodCanAccessPendingLeaves() throws Exception {
            when(leaveService.getPendingHodLeaves()).thenReturn(Collections.emptyList());
            mockMvc.perform(get("/api/leaves/hod/pending"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "student@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("STUDENT role accessing /api/leaves/hod/pending returns 403 Forbidden")
        void testStudentCannotAccessPendingHodLeaves() throws Exception {
            mockMvc.perform(get("/api/leaves/hod/pending"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "student@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("STUDENT role accessing non-configured endpoint returns 403 Forbidden (Default Deny)")
        void testDefaultDenyOnUnmatchedApiRoute() throws Exception {
            mockMvc.perform(get("/api/system/settings"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("2. Ownership and IDOR Security Tests")
    class OwnershipIdorTests {

        @Test
        @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("Student A attempting to access Student B marks gets 403 Forbidden via service check")
        void testStudentCannotAccessOtherStudentMarks() throws Exception {
            when(marksService.getMarksForStudent(eq(2L), anyBoolean()))
                    .thenThrow(new AccessDeniedException("Access denied: You do not have permission to view or manage this student's data"));

            mockMvc.perform(get("/api/marks/student/2"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("Student A accessing own marks succeeds with 200 OK")
        void testStudentCanAccessOwnMarks() throws Exception {
            MarkResponseDto dto = new MarkResponseDto();
            dto.setId(1L);
            dto.setStudentId(1L);
            dto.setMarks(88.0);
            dto.setStatus(MarkStatus.PUBLISHED);
            when(marksService.getMarksForStudent(eq(1L), anyBoolean()))
                    .thenReturn(List.of(dto));

            mockMvc.perform(get("/api/marks/student/1"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("Student A attempting to view Student B leaves gets 403 Forbidden")
        void testStudentCannotAccessOtherStudentLeaves() throws Exception {
            when(leaveService.getStudentLeaves(eq(2L)))
                    .thenThrow(new AccessDeniedException("Access denied: You do not have permission to view or manage this student's data"));

            mockMvc.perform(get("/api/leaves/student/2"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("Student A attempting to submit leave for Student B gets 403 Forbidden")
        void testStudentCannotSubmitLeaveForOtherStudent() throws Exception {
            when(leaveService.submitLeave(any(SubmitLeaveRequestDto.class)))
                    .thenThrow(new AccessDeniedException("Students can only submit leave requests for themselves"));

            mockMvc.perform(post("/api/leaves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"studentId\": 2, \"fromDate\": \"2026-11-01\", \"toDate\": \"2026-11-03\", \"reason\": \"Medical checkup\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("Student A attempting to view Student B attendance history gets 403 Forbidden")
        void testStudentCannotAccessOtherStudentAttendance() throws Exception {
            when(attendanceService.getRecordsForStudent(eq(2L)))
                    .thenThrow(new AccessDeniedException("Access denied: You do not have permission to view or manage this student's data"));

            mockMvc.perform(get("/api/attendance/sessions/records/student/2"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "studentA@intellicampus.edu", roles = {"STUDENT"})
        @DisplayName("Student A attempting to view Student B credentials gets 403 Forbidden")
        void testStudentCannotAccessOtherStudentCredentials() throws Exception {
            when(credentialService.getDocumentsForStudent(eq(2L)))
                    .thenThrow(new AccessDeniedException("Access denied: You do not have permission to view or manage this student's data"));

            mockMvc.perform(get("/api/credentials/student/2"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "mentorA@intellicampus.edu", roles = {"MENTOR"})
        @DisplayName("Mentor reviewing non-assigned student leave gets 403 Forbidden")
        void testMentorCannotReviewNonMenteeLeave() throws Exception {
            when(leaveService.processMentorReview(eq(5L), any(LeaveDecisionDto.class)))
                    .thenThrow(new AccessDeniedException("Mentors may only review leave requests for their assigned mentees"));

            mockMvc.perform(put("/api/leaves/5/mentor")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"approved\": true, \"comment\": \"Recommended\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "hod.cs@intellicampus.edu", roles = {"HOD"})
        @DisplayName("HOD deciding mark correction outside their department gets 403 Forbidden")
        void testHodCannotDecideCorrectionOutsideDepartment() throws Exception {
            when(marksService.decideCorrection(eq(8L), any(CorrectionDecisionDto.class)))
                    .thenThrow(new AccessDeniedException("HOD can only decide mark corrections within their own department"));

            mockMvc.perform(put("/api/marks/corrections/8/decision")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"approved\": true, \"comment\": \"Approved\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
