package com.intellicampus.auth;

import com.intellicampus.auth.controller.PrincipalAuthController;
import com.intellicampus.auth.controller.StaffAuthController;
import com.intellicampus.auth.dto.AuthResponseDto;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.dto.UserSummaryDto;
import com.intellicampus.auth.service.StaffAuthService;
import com.intellicampus.security.config.SecurityConfig;
import com.intellicampus.security.jwt.JwtAuthenticationFilter;
import com.intellicampus.security.jwt.JwtTokenProvider;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSingletonSecurityTest {

    @ExtendWith(MockitoExtension.class)
    @Nested
    @DisplayName("Principal Business Logic & Immutability Unit Tests")
    class PrincipalBusinessLogicTests {

        @Mock
        private UserRepository userRepository;

        @Mock
        private StudentProfileRepository studentProfileRepository;

        private final CsvUserValidator csvUserValidator = new CsvUserValidator();

        private UserService userService;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            userService = new UserService(
                    userRepository,
                    studentProfileRepository,
                    null,
                    null,
                    csvUserValidator,
                    null
            );
        }

        @Test
        @DisplayName("UserService.createUser rejects Role.PRINCIPAL with IllegalArgumentException")
        void testCreateUserRejectsPrincipalRole() {
            CreateUserRequestDto req = new CreateUserRequestDto();
            req.setUserCode("PRIN-HACK-01");
            req.setName("Malicious Principal");
            req.setEmail("hack.principal@college.edu");
            req.setRole(Role.PRINCIPAL);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> userService.createUser(req));
            assertTrue(ex.getMessage().contains("PRINCIPAL accounts"));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("CsvUserValidator and bulk CSV import strictly reject rows declaring role=PRINCIPAL")
        void testCsvImportRejectsPrincipalRole() {
            List<String> csvLines = List.of(
                    "userCode,name,email,department,rollNo,role",
                    "PRIN-CSV-01,Injected Principal,principal.injected@college.edu,CS,PRIN-01,PRINCIPAL"
            );

            CsvUserValidator.CsvValidationResult validation = csvUserValidator.validateCsvLines(csvLines);
            assertTrue(validation.hasErrors(), "CSV validation must flag error when role is PRINCIPAL");
            assertTrue(validation.getErrors().get(0).contains("Creating PRINCIPAL role via CSV import is strictly prohibited"));

            CsvImportResultDto result = userService.importCsv(csvLines);
            assertEquals(0, result.getImportedCount());
            assertEquals(1, result.getFailedCount());
        }

        @Test
        @DisplayName("UserService.deleteUser throws SecurityException when targeting a PRINCIPAL")
        void testDeleteUserRejectsPrincipal() {
            User principal = new User("PRIN-001", Role.PRINCIPAL, "College Principal", "principal@college.edu", "hash", null);
            principal.setId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(principal));

            SecurityException ex = assertThrows(SecurityException.class, () -> userService.deleteUser(1L));
            assertTrue(ex.getMessage().contains("Principal account cannot be deleted"));
            verify(userRepository, never()).delete(principal);
        }

        @Test
        @DisplayName("UserService.updateUserStatus throws SecurityException when attempting to deactivate a PRINCIPAL")
        void testDeactivatePrincipalRejects() {
            User principal = new User("PRIN-001", Role.PRINCIPAL, "College Principal", "principal@college.edu", "hash", null);
            principal.setId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(principal));

            SecurityException ex = assertThrows(SecurityException.class, () -> userService.updateUserStatus(1L, UserStatus.DISABLED));
            assertTrue(ex.getMessage().contains("Principal account cannot be deactivated"));
            verify(userRepository, never()).save(principal);
        }

        @Test
        @DisplayName("UserService.updateUserRole prohibits demoting PRINCIPAL or promoting another user to PRINCIPAL")
        void testRoleChangeGuards() {
            User principal = new User("PRIN-001", Role.PRINCIPAL, "College Principal", "principal@college.edu", "hash", null);
            principal.setId(1L);
            when(userRepository.findById(1L)).thenReturn(Optional.of(principal));

            User faculty = new User("FAC-001", Role.FACULTY, "Faculty Member", "fac@college.edu", "hash", null);
            faculty.setId(2L);
            when(userRepository.findById(2L)).thenReturn(Optional.of(faculty));

            // Prohibit demotion of PRINCIPAL
            SecurityException exDemote = assertThrows(SecurityException.class, () -> userService.updateUserRole(1L, Role.HOD));
            assertTrue(exDemote.getMessage().contains("demoted or changed"));

            // Prohibit promotion of non-principal to PRINCIPAL
            SecurityException exPromote = assertThrows(SecurityException.class, () -> userService.updateUserRole(2L, Role.PRINCIPAL));
            assertTrue(exPromote.getMessage().contains("Promoting an account to PRINCIPAL is strictly prohibited"));
        }
    }

    @WebMvcTest(controllers = {PrincipalAuthController.class, StaffAuthController.class})
    @Import({SecurityConfig.class, com.intellicampus.common.exception.GlobalExceptionHandler.class})
    @Nested
    @DisplayName("Principal Web and API Routing Security Tests")
    class PrincipalRoutingSecurityTests {

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
        @DisplayName("Principal setup status route /api/auth/principal/setup-status returns status")
        void testPrincipalSetupStatusRoute() throws Exception {
            when(staffAuthService.getPrincipalSetupStatus()).thenReturn(new com.intellicampus.auth.dto.PrincipalSetupStatusDto(true));

            mockMvc.perform(get("/api/auth/principal/setup-status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.setupRequired").value(true));
        }

        @Test
        @DisplayName("POST /api/auth/principal/login with valid PRINCIPAL credentials returns 200 OK")
        void testPrincipalLoginSuccess() throws Exception {
            UserSummaryDto summary = new UserSummaryDto(
                    1L, "PRIN-001", "College Principal", "principal@college.edu",
                    Role.PRINCIPAL, null, "Principal Administration", null, null
            );
            AuthResponseDto authResponse = new AuthResponseDto("jwt.token.mock", "refresh.token.mock", 900L, summary);

            when(staffAuthService.loginPrincipal(any(LoginRequestDto.class))).thenReturn(authResponse);

            mockMvc.perform(post("/api/auth/principal/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"principal@college.edu\",\"password\":\"ValidPrincipalPass123!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("jwt.token.mock"))
                    .andExpect(jsonPath("$.user.role").value("PRINCIPAL"));
        }

        @Test
        @DisplayName("POST /api/auth/principal/login with non-principal or invalid credentials returns 401 Unauthorized")
        void testPrincipalLoginRejectsNonPrincipalWithGeneric401() throws Exception {
            when(staffAuthService.loginPrincipal(any(LoginRequestDto.class)))
                    .thenThrow(new BadCredentialsException("Invalid email or password"));

            mockMvc.perform(post("/api/auth/principal/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"faculty.doe@intellicampus.edu\",\"password\":\"Pass123!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
