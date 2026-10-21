package com.intellicampus.auth;

import com.intellicampus.auth.controller.AdminAuthController;
import com.intellicampus.auth.controller.StaffAuthController;
import com.intellicampus.auth.dto.*;
import com.intellicampus.auth.service.JwtTokenProvider;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.dto.CsvImportResultDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.model.UserStatus;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.UserService;
import com.intellicampus.user.validation.CsvUserValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSingletonSecurityTest {

    @Nested
    @DisplayName("Service-Layer Admin Protection Tests")
    class ServiceLayerProtectionTests {

        private final UserRepository userRepository = mock(UserRepository.class);
        private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
        private final CsvUserValidator csvUserValidator = new CsvUserValidator();
        private final UserService userService = new UserService(
                userRepository,
                studentProfileRepository,
                null,
                null,
                csvUserValidator
        );

        @Test
        @DisplayName("UserService.createUser rejects Role.ADMIN with IllegalArgumentException")
        void testCreateUserRejectsAdminRole() {
            CreateUserRequestDto req = new CreateUserRequestDto();
            req.setUserCode("ADM-HACK-01");
            req.setName("Malicious Admin");
            req.setEmail("hack.admin@intellicampus.edu");
            req.setRole(Role.ADMIN);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
            assertTrue(ex.getMessage().contains("ADMIN accounts"));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("CsvUserValidator and bulk CSV import strictly reject rows declaring role=ADMIN")
        void testCsvImportRejectsAdminRole() {
            List<String> csvLines = List.of(
                    "userCode,name,email,department,rollNo,role",
                    "ADM-CSV-01,Injected Admin,admin.injected@intellicampus.edu,CS,ADMIN-01,ADMIN"
            );

            CsvUserValidator.CsvValidationResult validation = csvUserValidator.validateCsvLines(csvLines);
            assertTrue(validation.hasErrors(), "CSV validation must flag error when role is ADMIN");
            assertTrue(validation.getErrors().get(0).contains("Creating ADMIN role via CSV import is strictly prohibited"));

            CsvImportResultDto result = userService.importCsv(csvLines);
            assertEquals(0, result.getImportedCount());
            assertEquals(1, result.getFailedCount());
        }

        @Test
        @DisplayName("UserService.deleteUser throws SecurityException when targeting an ADMIN")
        void testDeleteUserRejectsAdmin() {
            User admin = new User("ADM-SUPER-001", Role.ADMIN, "System Admin", "admin@intellicampus.edu", "hash", null);
            admin.setId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            SecurityException ex = assertThrows(SecurityException.class, () -> userService.deleteUser(1L));
            assertTrue(ex.getMessage().contains("System Administrator account cannot be deleted"));
            verify(userRepository, never()).delete(admin);
        }

        @Test
        @DisplayName("UserService.updateUserStatus throws SecurityException when attempting to deactivate an ADMIN")
        void testDeactivateAdminRejects() {
            User admin = new User("ADM-SUPER-001", Role.ADMIN, "System Admin", "admin@intellicampus.edu", "hash", null);
            admin.setId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            SecurityException ex = assertThrows(SecurityException.class, () -> userService.updateUserStatus(1L, UserStatus.DISABLED));
            assertTrue(ex.getMessage().contains("System Administrator account cannot be deactivated"));
            verify(userRepository, never()).save(admin);
        }

        @Test
        @DisplayName("UserService.updateUserRole prohibits demoting ADMIN or promoting another user to ADMIN")
        void testRoleChangeGuards() {
            User admin = new User("ADM-SUPER-001", Role.ADMIN, "System Admin", "admin@intellicampus.edu", "hash", null);
            admin.setId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            User faculty = new User("FAC-001", Role.FACULTY, "Faculty Member", "fac@intellicampus.edu", "hash", null);
            faculty.setId(2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(faculty));

            // Prohibit demotion of ADMIN
            SecurityException exDemote = assertThrows(SecurityException.class, () -> userService.updateUserRole(1L, Role.HOD));
            assertTrue(exDemote.getMessage().contains("demoted or changed"));

            // Prohibit promotion of non-admin to ADMIN
            SecurityException exPromote = assertThrows(SecurityException.class, () -> userService.updateUserRole(2L, Role.ADMIN));
            assertTrue(exPromote.getMessage().contains("Promoting an account to ADMIN is strictly prohibited"));
        }
    }

    @WebMvcTest(controllers = {AdminAuthController.class, StaffAuthController.class})
    @Import({SecurityConfig.class, com.intellicampus.common.exception.GlobalExceptionHandler.class})
    @Nested
    @DisplayName("Admin Web and API Routing Security Tests")
    class AdminRoutingSecurityTests {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private StaffAuthService staffAuthService;

        @MockBean
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        @MockBean
        private UserRepository userRepository;

        @org.junit.jupiter.api.BeforeEach
        void setUp() throws Exception {
            org.mockito.Mockito.doAnswer(invocation -> {
                jakarta.servlet.ServletRequest request = invocation.getArgument(0);
                jakarta.servlet.ServletResponse response = invocation.getArgument(1);
                jakarta.servlet.FilterChain filterChain = invocation.getArgument(2);
                filterChain.doFilter(request, response);
                return null;
            }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        }

        @Test
        @DisplayName("Admin register route /api/auth/admin/register does NOT exist (returns 404)")
        void testAdminRegisterRouteDoesNotExist() throws Exception {
            mockMvc.perform(post("/api/auth/admin/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Admin\",\"email\":\"newadmin@test.com\",\"password\":\"Pass@1234\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("POST /api/auth/admin/login with valid ADMIN credentials returns 200 OK")
        void testAdminLoginSuccess() throws Exception {
            UserSummaryDto summary = new UserSummaryDto(
                    1L, "ADM-SUPER-001", "System SuperAdmin", "admin@intellicampus.edu",
                    Role.ADMIN, null, "Central Administration", null, null
            );
            AuthResponseDto authResponse = new AuthResponseDto("jwt.token.mock", "refresh.token.mock", 900L, summary);

            when(staffAuthService.loginAdmin(any(LoginRequestDto.class))).thenReturn(authResponse);

            mockMvc.perform(post("/api/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"admin@intellicampus.edu\",\"password\":\"ValidAdminPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("jwt.token.mock"))
                    .andExpect(jsonPath("$.user.role").value("ADMIN"));
        }

        @Test
        @DisplayName("POST /api/auth/admin/login with non-admin or invalid credentials returns 401 Unauthorized")
        void testAdminLoginRejectsNonAdminWithGeneric401() throws Exception {
            when(staffAuthService.loginAdmin(any(LoginRequestDto.class)))
                    .thenThrow(new BadCredentialsException("Invalid email or password"));

            mockMvc.perform(post("/api/auth/admin/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"faculty.doe@intellicampus.edu\",\"password\":\"Pass123!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
