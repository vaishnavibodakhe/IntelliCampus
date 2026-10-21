package com.intellicampus.auth;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.auth.dto.AuthResponseDto;
import com.intellicampus.auth.dto.LoginRequestDto;
import com.intellicampus.auth.dto.RegisterRequestDto;
import com.intellicampus.auth.service.AuthService;
import com.intellicampus.auth.service.JwtTokenProvider;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DivisionRepository divisionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private Department csDept;
    private Division csDiv;

    @BeforeEach
    void setUp() {
        csDept = new Department("CS", "Computer Science");
        csDept.setId(1L);
        csDiv = new Division(csDept, 3, "Year 3 Div A");
        csDiv.setId(1L);
    }

    @Nested
    @DisplayName("Student Registration Tests")
    class RegisterTests {

        @Test
        @DisplayName("Successfully registers student with hashed password and generates JWT")
        void testRegisterStudentSuccess() {
            RegisterRequestDto req = new RegisterRequestDto(
                    "Pooja Deshmukh",
                    "pooja.cs@intellicampus.edu",
                    "StrongPass123!",
                    "2024-CS-042",
                    "Computer Science",
                    3
            );

            when(userRepository.existsByEmail("pooja.cs@intellicampus.edu")).thenReturn(false);
            when(studentProfileRepository.existsByRollNo("2024-CS-042")).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));
            when(divisionRepository.findByNameAndDepartmentId("Year 3 Div A", 1L)).thenReturn(Optional.of(csDiv));
            when(passwordEncoder.encode("StrongPass123!")).thenReturn("$2a$10$encodedHash123");

            User savedUser = new User("STU-2024-CS-042", Role.STUDENT, "Pooja Deshmukh", "pooja.cs@intellicampus.edu", "$2a$10$encodedHash123", csDept);
            savedUser.setId(10L);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            when(jwtTokenProvider.generateAccessToken(eq(savedUser), eq("2024-CS-042"))).thenReturn("mock.jwt.access");
            when(jwtTokenProvider.generateRefreshToken(eq(savedUser))).thenReturn("mock.jwt.refresh");
            when(jwtTokenProvider.getAccessExpirationSeconds()).thenReturn(900L);

            AuthResponseDto response = authService.registerStudent(req);

            assertNotNull(response);
            assertEquals("mock.jwt.access", response.getAccessToken());
            assertEquals("mock.jwt.refresh", response.getRefreshToken());
            assertEquals("Pooja Deshmukh", response.getUser().getName());
            assertEquals("2024-CS-042", response.getUser().getRollNo());
            assertEquals("Computer Science", response.getUser().getDepartment());
            verify(studentProfileRepository).save(any(StudentProfile.class));
        }

        @Test
        @DisplayName("Rejects registration when email is already in use")
        void testRegisterDuplicateEmail() {
            RegisterRequestDto req = new RegisterRequestDto(
                    "Duplicate User",
                    "taken@intellicampus.edu",
                    "Pass123",
                    "CS-999",
                    "Computer Science",
                    1
            );

            when(userRepository.existsByEmail("taken@intellicampus.edu")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    authService.registerStudent(req)
            );
            assertTrue(ex.getMessage().contains("Email is already registered"));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rejects registration when roll number is already in use")
        void testRegisterDuplicateRollNo() {
            RegisterRequestDto req = new RegisterRequestDto(
                    "Duplicate Roll",
                    "new@intellicampus.edu",
                    "Pass123",
                    "CS-104",
                    "Computer Science",
                    3
            );

            when(userRepository.existsByEmail("new@intellicampus.edu")).thenReturn(false);
            when(studentProfileRepository.existsByRollNo("CS-104")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    authService.registerStudent(req)
            );
            assertTrue(ex.getMessage().contains("Roll number is already registered"));
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Student Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Successfully logs in student with valid credentials and returns tokens")
        void testLoginSuccess() {
            User user = new User("STU-CS-104", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", "$2a$10$hashedPass", csDept);
            user.setId(5L);
            StudentProfile profile = new StudentProfile(user, "CS-104", csDiv, null);

            when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("Password@123", "$2a$10$hashedPass")).thenReturn(true);
            when(studentProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
            when(jwtTokenProvider.generateAccessToken(eq(user), eq("CS-104"))).thenReturn("access.token.jwt");
            when(jwtTokenProvider.generateRefreshToken(eq(user))).thenReturn("refresh.token.jwt");
            when(jwtTokenProvider.getAccessExpirationSeconds()).thenReturn(900L);

            LoginRequestDto req = new LoginRequestDto("aarav@intellicampus.edu", "Password@123");
            AuthResponseDto response = authService.login(req);

            assertNotNull(response);
            assertEquals("access.token.jwt", response.getAccessToken());
            assertEquals("CS-104", response.getUser().getRollNo());
            assertEquals(0, user.getFailedAttempts());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Fails login with incorrect password and increments failed attempts")
        void testLoginWrongPassword() {
            User user = new User("STU-CS-104", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", "$2a$10$hashedPass", csDept);
            user.setFailedAttempts(2);

            when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedPass")).thenReturn(false);

            LoginRequestDto req = new LoginRequestDto("aarav@intellicampus.edu", "WrongPassword");
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    authService.login(req)
            );

            assertEquals("Invalid email or password", ex.getMessage());
            assertEquals(3, user.getFailedAttempts());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Locks account when consecutive failed attempts reach 5")
        void testLoginLocksAccountAfter5Attempts() {
            User user = new User("STU-CS-104", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", "$2a$10$hashedPass", csDept);
            user.setFailedAttempts(4);

            when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("WrongPassword", "$2a$10$hashedPass")).thenReturn(false);

            LoginRequestDto req = new LoginRequestDto("aarav@intellicampus.edu", "WrongPassword");
            assertThrows(BadCredentialsException.class, () -> authService.login(req));

            assertEquals(5, user.getFailedAttempts());
            assertEquals(UserStatus.LOCKED, user.getStatus());
            assertNotNull(user.getLockedUntil());
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("Rejects login immediately when account is locked")
        void testLoginLockedAccountRejection() {
            User user = new User("STU-CS-104", Role.STUDENT, "Aarav Shah", "aarav@intellicampus.edu", "$2a$10$hashedPass", csDept);
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));

            when(userRepository.findByEmail("aarav@intellicampus.edu")).thenReturn(Optional.of(user));

            LoginRequestDto req = new LoginRequestDto("aarav@intellicampus.edu", "AnyPass");
            LockedException ex = assertThrows(LockedException.class, () -> authService.login(req));

            assertTrue(ex.getMessage().contains("temporarily locked"));
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("Fails login when user email does not exist")
        void testLoginUnknownEmail() {
            when(userRepository.findByEmail("ghost@intellicampus.edu")).thenReturn(Optional.empty());

            LoginRequestDto req = new LoginRequestDto("ghost@intellicampus.edu", "Pass123");
            BadCredentialsException ex = assertThrows(BadCredentialsException.class, () ->
                    authService.login(req)
            );
            assertEquals("Invalid email or password", ex.getMessage());
        }
    }
}
