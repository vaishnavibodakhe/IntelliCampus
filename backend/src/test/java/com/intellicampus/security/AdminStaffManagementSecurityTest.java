package com.intellicampus.security;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.user.controller.AdminStaffController;
import com.intellicampus.user.dto.*;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.AdminStaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminStaffController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
public class AdminStaffManagementSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminStaffService adminStaffService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private StudentProfileRepository studentProfileRepository;

    @MockBean
    private com.intellicampus.security.jwt.JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.intellicampus.user.repository.RevokedTokenRepository revokedTokenRepository;

    @MockBean
    private com.intellicampus.user.repository.TokenDenylistRepository tokenDenylistRepository;

    @Nested
    @DisplayName("1. Public Staff Registration Removal & Route Protection")
    class PublicRegistrationRemovalTests {

        @Test
        @DisplayName("Removed staff register route /api/auth/staff/register is rejected (401/403/404)")
        void testStaffRegisterRouteRejected() throws Exception {
            mockMvc.perform(post("/api/auth/staff/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"staff@test.com\",\"password\":\"Pass123!\"}"))
                    .andExpect(status().is(anyOf(is(401), is(403), is(404))));
        }

        @Test
        @DisplayName("Removed faculty register route /api/faculty/register is rejected (401/403/404)")
        void testFacultyRegisterRouteRejected() throws Exception {
            mockMvc.perform(post("/api/faculty/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"faculty@test.com\",\"password\":\"Pass123!\"}"))
                    .andExpect(status().is(anyOf(is(401), is(403), is(404))));
        }

        @Test
        @DisplayName("Removed mentor register route /api/mentor/register is rejected (401/403/404)")
        void testMentorRegisterRouteRejected() throws Exception {
            mockMvc.perform(post("/api/mentor/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"mentor@test.com\",\"password\":\"Pass123!\"}"))
                    .andExpect(status().is(anyOf(is(401), is(403), is(404))));
        }
    }

    @Nested
    @DisplayName("2. Role-Based Access Control on /api/admin/staff")
    class RbacStaffManagementTests {

        @Test
        @DisplayName("Anonymous access to /api/admin/staff returns 401 Unauthorized")
        void testAnonymousAccessReturns401() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "student@test.com", roles = "STUDENT")
        @DisplayName("STUDENT role gets 403 Forbidden on /api/admin/staff")
        void testStudentGets403() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "faculty@test.com", roles = "FACULTY")
        @DisplayName("FACULTY role gets 403 Forbidden on /api/admin/staff")
        void testFacultyGets403() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "mentor@test.com", roles = "MENTOR")
        @DisplayName("MENTOR role gets 403 Forbidden on /api/admin/staff")
        void testMentorGets403() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "hod@test.com", roles = "HOD")
        @DisplayName("HOD role gets 403 Forbidden on /api/admin/staff")
        void testHodGets403() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin@test.com", roles = "ADMIN")
        @DisplayName("ADMIN role gets 200 OK on GET /api/admin/staff")
        void testAdminGets200() throws Exception {
            when(adminStaffService.getAllStaff(any(), any(), any(), any())).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("3. Controller-Level Single Admin & Validation Guards")
    class AdminStaffControllerGuardTests {

        @Test
        @WithMockUser(username = "admin@test.com", roles = "ADMIN")
        @DisplayName("Creating staff with role ADMIN is rejected with 400 Bad Request")
        void testCreateAdminRejected() throws Exception {
            when(adminStaffService.createStaff(any(), any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be HOD, FACULTY, or MENTOR."));

            mockMvc.perform(post("/api/admin/staff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Fake Admin\",\"email\":\"hacked@campus.edu\",\"role\":\"ADMIN\",\"department\":\"CSE\",\"employeeCode\":\"EMP-001\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "admin@test.com", roles = "ADMIN")
        @DisplayName("Creating valid staff (HOD) returns 201 Created with temporary password")
        void testCreateValidStaffReturns201() throws Exception {
            AdminStaffResponseDto created = new AdminStaffResponseDto(
                    10L, "HOD-101", "Dr. Jane", "jane@campus.edu", Role.HOD,
                    "Computer Science", 1L, UserStatus.ACTIVE, true, "Temp#Pass1234567",
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(adminStaffService.createStaff(any(), any())).thenReturn(created);

            mockMvc.perform(post("/api/admin/staff")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Dr. Jane\",\"email\":\"jane@campus.edu\",\"role\":\"HOD\",\"department\":\"Computer Science\",\"employeeCode\":\"HOD-101\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.role").value("HOD"))
                    .andExpect(jsonPath("$.mustChangePassword").value(true))
                    .andExpect(jsonPath("$.temporaryPassword").value("Temp#Pass1234567"));
        }

        @Test
        @WithMockUser(username = "admin@test.com", roles = "ADMIN")
        @DisplayName("Resetting staff password returns new temporary password")
        void testResetStaffPasswordReturnsNewTempPassword() throws Exception {
            AdminStaffResponseDto result = new AdminStaffResponseDto(
                    10L, "HOD-101", "Dr. Jane", "jane@campus.edu", Role.HOD,
                    "Computer Science", 1L, UserStatus.ACTIVE, true, "Reset#Pass9876543",
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(adminStaffService.resetStaffPassword(eq(10L), any())).thenReturn(result);

            mockMvc.perform(post("/api/admin/staff/10/reset-password"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mustChangePassword").value(true))
                    .andExpect(jsonPath("$.temporaryPassword").value("Reset#Pass9876543"));
        }

        @Test
        @WithMockUser(username = "admin@test.com", roles = "ADMIN")
        @DisplayName("Updating staff status to DISABLED returns updated status")
        void testUpdateStaffStatus() throws Exception {
            AdminStaffResponseDto updated = new AdminStaffResponseDto(
                    10L, "HOD-101", "Dr. Jane", "jane@campus.edu", Role.HOD,
                    "Computer Science", 1L, UserStatus.DISABLED, false, null,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            when(adminStaffService.updateStaffStatus(eq(10L), any(), any())).thenReturn(updated);

            mockMvc.perform(patch("/api/admin/staff/10/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"DISABLED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISABLED"));
        }

        @Test
        @WithMockUser(username = "admin@test.com", roles = "ADMIN")
        @DisplayName("Soft-deleting staff returns 200 OK")
        void testSoftDeleteStaff() throws Exception {
            doNothing().when(adminStaffService).deleteStaff(eq(10L), any());

            mockMvc.perform(delete("/api/admin/staff/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Staff account soft-deleted successfully"));
        }
    }
}
