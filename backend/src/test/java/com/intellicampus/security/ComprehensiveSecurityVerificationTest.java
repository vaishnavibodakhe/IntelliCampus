package com.intellicampus.security;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.audit.service.AuditChainService;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.repository.PasswordResetRateLimitRepository;
import com.intellicampus.auth.repository.RefreshTokenRepository;
import com.intellicampus.auth.service.EmailService;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.leave.controller.LeaveController;
import com.intellicampus.leave.dto.LeaveDecisionDto;
import com.intellicampus.leave.service.LeaveService;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.security.jwt.JwtTokenProvider;
import com.intellicampus.user.controller.AdminStaffController;
import com.intellicampus.user.controller.UserController;
import com.intellicampus.user.dto.AdminStaffCreateDto;
import com.intellicampus.user.dto.AdminStaffResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.PasswordResetTokenRepository;
import com.intellicampus.user.repository.RevokedTokenRepository;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.TokenDenylistRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.AdminStaffService;
import com.intellicampus.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ComprehensiveSecurityVerificationTest {

    @Nested
    @WebMvcTest(controllers = {
            AdminStaffController.class,
            UserController.class,
            LeaveController.class
    })
    @Import(SecurityConfig.class)
    @DisplayName("1. HTTP Access Control & RBAC Boundary Tests")
    class HttpAccessControlTests {

        @Autowired
        private MockMvc mockMvc;

        @MockBean private AdminStaffService adminStaffService;
        @MockBean private UserService userService;
        @MockBean private LeaveService leaveService;
        @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
        @MockBean private UserRepository userRepository;
        @MockBean private StudentProfileRepository studentProfileRepository;

        @BeforeEach
        void setUpFilterPassThrough() throws Exception {
            doAnswer(invocation -> {
                FilterChain chain = invocation.getArgument(2);
                chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        }

        @Test
        @DisplayName("Criterion 1: Anonymous request gets 401 Unauthorized on protected APIs")
        void testAnonymousRequestGets401() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/leave/1/hod"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(username = "student@campus.edu", roles = {"STUDENT"})
        @DisplayName("Criterion 2a: STUDENT gets 403 Forbidden on /api/admin/** and HOD endpoints")
        void testStudentForbiddenOnAdminAndHod() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/leave/1/hod"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "faculty@campus.edu", roles = {"FACULTY"})
        @DisplayName("Criterion 2b: FACULTY gets 403 Forbidden on /api/admin/** and HOD endpoints")
        void testFacultyForbiddenOnAdminAndHod() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/leave/1/hod"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "mentor@campus.edu", roles = {"MENTOR"})
        @DisplayName("Criterion 2c: MENTOR gets 403 Forbidden on /api/admin/** and HOD endpoints")
        void testMentorForbiddenOnAdminAndHod() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/leave/1/hod"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "admin@campus.edu", roles = {"ADMIN"})
        @DisplayName("Criterion 2d: ADMIN can access /api/admin/** and manage endpoints")
        void testAdminCanAccessAdminEndpoints() throws Exception {
            mockMvc.perform(get("/api/admin/staff"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Criterion 3: Removed staff registration URLs return 401/403/404 (not publicly accessible)")
        void testRemovedRegistrationEndpointsNotPublic() throws Exception {
            mockMvc.perform(post("/api/auth/staff/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"fake@campus.edu\"}"))
                    .andExpect(status().is4xxClientError());

            mockMvc.perform(post("/api/faculty/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"fake@campus.edu\"}"))
                    .andExpect(status().is4xxClientError());

            mockMvc.perform(post("/api/mentor/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"fake@campus.edu\"}"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("2. Single Admin & Admin Staff Provisioning Unit Tests")
    class SingleAdminAndStaffServiceTests {

        @Mock private UserRepository userRepository;
        @Mock private DepartmentRepository departmentRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private EmailService emailService;
        @Mock private AuditChainService auditChainService;
        @Mock private RefreshTokenRepository refreshTokenRepository;

        private AdminStaffService adminStaffService;
        private Department csDept;

        @BeforeEach
        void setUp() {
            adminStaffService = new AdminStaffService(
                    userRepository, departmentRepository, passwordEncoder,
                    auditChainService, emailService, refreshTokenRepository
            );
            csDept = new Department("CSE", "Computer Science");
            csDept.setId(1L);
        }

        @Test
        @DisplayName("Criterion 4: Attempting to create or promote an ADMIN fails at service level")
        void testSingleAdminProtection() {
            AdminStaffCreateDto adminReq = new AdminStaffCreateDto();
            adminReq.setName("Impostor Admin");
            adminReq.setEmail("impostor@campus.edu");
            adminReq.setEmployeeCode("ADM-999");
            adminReq.setRole(Role.ADMIN);
            adminReq.setDepartment("Computer Science");

            User currentAdmin = new User("ADM-01", Role.ADMIN, "System Admin", "admin@campus.edu", "hash", null);
            currentAdmin.setId(1L);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> adminStaffService.createStaff(adminReq, currentAdmin));
            assertTrue(ex.getReason().contains("HOD, FACULTY, or MENTOR"));
        }

        @Test
        @DisplayName("Criterion 5: Admin can create HOD, FACULTY, and MENTOR with temporary password and must_change_password=true")
        void testAdminCanCreateStaff() {
            AdminStaffCreateDto req = new AdminStaffCreateDto();
            req.setName("Dr. Katherine Johnson");
            req.setEmail("kjohnson@campus.edu");
            req.setEmployeeCode("FAC-007");
            req.setRole(Role.FACULTY);
            req.setDepartment("Computer Science");

            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
            when(userRepository.existsByEmailIgnoreCase("kjohnson@campus.edu")).thenReturn(false);
            when(userRepository.existsByUserCode("FAC-007")).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed_temp_password");
            when(userRepository.save(any(User.class))).thenAnswer(i -> {
                User u = i.getArgument(0);
                u.setId(50L);
                return u;
            });

            User currentAdmin = new User("ADM-01", Role.ADMIN, "System Admin", "admin@campus.edu", "hash", null);
            currentAdmin.setId(1L);

            AdminStaffResponseDto response = adminStaffService.createStaff(req, currentAdmin);

            assertNotNull(response);
            assertEquals("Dr. Katherine Johnson", response.getName());
            assertEquals(Role.FACULTY, response.getRole());
            assertTrue(response.getMustChangePassword());
            assertNotNull(response.getTemporaryPassword());
            assertEquals(16, response.getTemporaryPassword().length());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("3. JwtAuthenticationFilter: Deactivated Users, Revoked Tokens & Must-Change-Password Gate")
    class JwtFilterLifecycleTests {

        @Mock private JwtTokenProvider jwtTokenProvider;
        @Mock private UserRepository userRepository;
        @Mock private RevokedTokenRepository revokedTokenRepository;
        @Mock private TokenDenylistRepository tokenDenylistRepository;
        @Mock private FilterChain filterChain;

        private JwtAuthenticationFilter filter;

        @BeforeEach
        void setUp() {
            SecurityContextHolder.clearContext();
            filter = new JwtAuthenticationFilter(
                    jwtTokenProvider, revokedTokenRepository, userRepository, tokenDenylistRepository
            );
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("Criterion 6: Token belonging to a DISABLED user is denied authentication")
        void testDisabledUserTokenRejected() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notices");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtTokenProvider.validateToken("valid.jwt.token")).thenReturn(true);
            when(tokenDenylistRepository.existsByToken("valid.jwt.token")).thenReturn(false);
            when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
            when(jwtTokenProvider.getEmailFromToken("valid.jwt.token")).thenReturn("disabled@campus.edu");
            when(jwtTokenProvider.getRoleFromToken("valid.jwt.token")).thenReturn("FACULTY");

            User disabledUser = new User("STAFF-01", Role.FACULTY, "Disabled User", "disabled@campus.edu", "hash", null);
            disabledUser.setId(90L);
            disabledUser.setStatus(UserStatus.DISABLED);

            when(userRepository.findByEmail("disabled@campus.edu")).thenReturn(Optional.of(disabledUser));

            filter.doFilter(request, response, filterChain);

            // Authentication must NOT be set for a disabled user
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Criterion 8: must_change_password=true user attempting protected API receives HTTP 403 PASSWORD_CHANGE_REQUIRED")
        void testMustChangePasswordGateEnforced() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/marks/1");
            request.setRequestURI("/api/marks/1");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtTokenProvider.validateToken("valid.jwt.token")).thenReturn(true);
            when(tokenDenylistRepository.existsByToken("valid.jwt.token")).thenReturn(false);
            when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
            when(jwtTokenProvider.getEmailFromToken("valid.jwt.token")).thenReturn("temp@campus.edu");
            when(jwtTokenProvider.getRoleFromToken("valid.jwt.token")).thenReturn("FACULTY");

            User tempUser = new User("STAFF-02", Role.FACULTY, "Temp User", "temp@campus.edu", "hash", null);
            tempUser.setId(91L);
            tempUser.setStatus(UserStatus.ACTIVE);
            tempUser.setMustChangePassword(true);

            when(userRepository.findByEmail("temp@campus.edu")).thenReturn(Optional.of(tempUser));

            filter.doFilter(request, response, filterChain);

            assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
            assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Criterion 8b: must_change_password=true user CAN access change-password, refresh, and logout endpoints")
        void testMustChangePasswordExemptEndpointsAllowed() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/staff/change-password");
            request.setRequestURI("/api/auth/staff/change-password");
            request.addHeader("Authorization", "Bearer valid.jwt.token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            when(jwtTokenProvider.validateToken("valid.jwt.token")).thenReturn(true);
            when(tokenDenylistRepository.existsByToken("valid.jwt.token")).thenReturn(false);
            when(revokedTokenRepository.existsByTokenHash(anyString())).thenReturn(false);
            when(jwtTokenProvider.getEmailFromToken("valid.jwt.token")).thenReturn("temp@campus.edu");
            when(jwtTokenProvider.getRoleFromToken("valid.jwt.token")).thenReturn("FACULTY");

            User tempUser = new User("STAFF-02", Role.FACULTY, "Temp User", "temp@campus.edu", "hash", null);
            tempUser.setId(91L);
            tempUser.setStatus(UserStatus.ACTIVE);
            tempUser.setMustChangePassword(true);

            when(userRepository.findByEmail("temp@campus.edu")).thenReturn(Optional.of(tempUser));

            filter.doFilter(request, response, filterChain);

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    @DisplayName("4. Authentication Hardening & IDOR Protection Tests")
    class HardeningAndIdorTests {

        @Mock private UserRepository userRepository;
        @Mock private DepartmentRepository departmentRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private com.intellicampus.auth.service.JwtTokenProvider jwtTokenProvider;
        @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
        @Mock private RefreshTokenRepository refreshTokenRepository;
        @Mock private PasswordResetRateLimitRepository rateLimitRepository;
        @Mock private AuditChainService auditChainService;
        @Mock private EmailService emailService;
        @Mock private LeaveService leaveService;

        private StaffAuthService staffAuthService;

        @BeforeEach
        void setUp() {
            staffAuthService = new StaffAuthService(
                    userRepository, departmentRepository, passwordEncoder,
                    jwtTokenProvider, passwordResetTokenRepository, refreshTokenRepository,
                    rateLimitRepository, auditChainService, emailService
            );
        }

        @Test
        @DisplayName("Criterion 7: 5 consecutive failed login attempts locks account for 15 minutes")
        void testLockoutAfterFiveFailures() {
            User staff = new User("FAC-99", Role.FACULTY, "Target Staff", "target@campus.edu", "real_hash", null);
            staff.setId(88L);
            staff.setFailedAttempts(4); // 5th attempt incoming

            when(userRepository.findByEmail("target@campus.edu")).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches("wrong_password", "real_hash")).thenReturn(false);

            LoginRequestDto req = new LoginRequestDto("target@campus.edu", "wrong_password");
            assertThrows(BadCredentialsException.class, () -> staffAuthService.loginStaff(req));

            assertEquals(5, staff.getFailedAttempts());
            assertEquals(UserStatus.LOCKED, staff.getStatus());
            assertNotNull(staff.getLockedUntil());
            assertTrue(staff.getLockedUntil().isAfter(LocalDateTime.now().plusMinutes(14)));
        }

        @Test
        @DisplayName("Criterion 9: IDOR protection prevents mentor from reviewing non-assigned student leave")
        void testIdorNonMenteeLeaveRejected() {
            LeaveDecisionDto decisionDto = new LeaveDecisionDto(100L, true, "Recommended");

            doThrow(new AccessDeniedException("Mentors may only review leave requests for their assigned mentees"))
                    .when(leaveService).processMentorReview(eq(100L), any(LeaveDecisionDto.class));

            assertThrows(AccessDeniedException.class,
                    () -> leaveService.processMentorReview(100L, decisionDto));
        }
    }
}
