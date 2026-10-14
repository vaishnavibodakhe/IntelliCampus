package com.intellicampus.user;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
import com.intellicampus.user.dto.CreateUserRequestDto;
import com.intellicampus.user.dto.CsvImportResultDto;
import com.intellicampus.user.dto.UserResponseDto;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import com.intellicampus.user.service.UserService;
import com.intellicampus.user.validation.CsvUserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for UserService covering user CRUD, search/role filtering,
 * and bulk CSV ingestion isolating malformed rows while importing valid records.
 * Compliant with IC-SRS-1.0 Section 8.5 and Test Case TC-09.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DivisionRepository divisionRepository;

    @Spy
    private CsvUserValidator csvUserValidator = new CsvUserValidator();

    @InjectMocks
    private UserService userService;

    private Department csDept;

    @BeforeEach
    void setUp() {
        csDept = new Department("CS", "Computer Science");
        ReflectionTestUtils.setField(csDept, "id", 1L);
    }

    @Nested
    @DisplayName("Bulk CSV Ingestion & Malformed Row Isolation Tests")
    class CsvIngestionTests {

        @Test
        @DisplayName("Criterion 1: Valid CSV rows are imported while malformed rows are isolated")
        void testCsvBulkImportWithValidAndMalformedRows() {
            List<String> csvLines = List.of(
                    "usercode,name,email,department,rollno",
                    "STU-001,Aarav Mehta,aarav@intellicampus.edu,Computer Science,2024-CS-001",
                    "STU-002,Priya Sharma,invalid-email-format,Computer Science,2024-CS-002", // Malformed email
                    "STU-003,Rohan Kulkarni,rohan@intellicampus.edu,Computer Science,2024-CS-003"
            );

            when(departmentRepository.findByName(anyString())).thenReturn(Optional.of(csDept));
            when(userRepository.existsByUserCode("STU-001")).thenReturn(false);
            when(userRepository.existsByEmail("aarav@intellicampus.edu")).thenReturn(false);
            when(userRepository.existsByUserCode("STU-003")).thenReturn(false);
            when(userRepository.existsByEmail("rohan@intellicampus.edu")).thenReturn(false);

            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 101L);
                return u;
            });

            CsvImportResultDto result = userService.importCsv(csvLines);

            assertNotNull(result);
            assertEquals(2, result.getImportedCount(), "Two valid records should be successfully imported");
            assertEquals(1, result.getFailedCount(), "One malformed record should be isolated as failure");
            assertFalse(result.getErrors().isEmpty(), "Errors list should capture the malformed row");
            assertTrue(result.getErrors().get(0).contains("Line 3"), "Error message should cite line number 3");
            assertTrue(result.getErrors().get(0).contains("Invalid email format"), "Error should cite invalid email");

            verify(userRepository, times(2)).save(any(User.class));
        }

        @Test
        @DisplayName("Criterion 1: CSV import rejects and isolates rows with duplicate email or userCode")
        void testCsvImportWithDuplicateRecords() {
            List<String> csvLines = List.of(
                    "usercode,name,email,department,rollno",
                    "STU-101,Neha Joshi,neha@intellicampus.edu,Computer Science,2024-CS-010",
                    "STU-102,Suresh Patil,neha@intellicampus.edu,Computer Science,2024-CS-011" // Duplicate email in batch
            );

            when(departmentRepository.findByName(anyString())).thenReturn(Optional.of(csDept));
            when(userRepository.existsByUserCode("STU-101")).thenReturn(false);
            when(userRepository.existsByEmail("neha@intellicampus.edu")).thenReturn(false);
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            CsvImportResultDto result = userService.importCsv(csvLines);

            assertEquals(1, result.getImportedCount());
            assertEquals(1, result.getFailedCount());
            assertTrue(result.getErrors().get(0).contains("Duplicate email"));
        }

        @Test
        @DisplayName("CSV import fails when mandatory headers are missing")
        void testCsvImportMissingHeaders() {
            List<String> csvLines = List.of(
                    "usercode,name,department", // Missing 'email' and 'rollno'
                    "STU-201,Kiran Rao,Computer Science"
            );

            CsvImportResultDto result = userService.importCsv(csvLines);

            assertEquals(0, result.getImportedCount());
            assertEquals(1, result.getFailedCount());
            assertTrue(result.getErrors().get(0).contains("Missing required CSV header"));
        }
    }

    @Nested
    @DisplayName("User Creation & Search Filtering Tests")
    class UserCrudTests {

        @Test
        @DisplayName("Should create user successfully when credentials and codes are unique")
        void testCreateUserSuccess() {
            CreateUserRequestDto req = new CreateUserRequestDto();
            req.setUserCode("FAC-888");
            req.setName("Dr. Anjali Verma");
            req.setEmail("anjali@intellicampus.edu");
            req.setRole(Role.FACULTY);
            req.setDepartment("Computer Science");

            when(userRepository.existsByUserCode("FAC-888")).thenReturn(false);
            when(userRepository.existsByEmail("anjali@intellicampus.edu")).thenReturn(false);
            when(departmentRepository.findByName("Computer Science")).thenReturn(Optional.of(csDept));

            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                ReflectionTestUtils.setField(u, "id", 50L);
                return u;
            });

            UserResponseDto response = userService.createUser(req);

            assertNotNull(response);
            assertEquals(50L, response.getId());
            assertEquals("Dr. Anjali Verma", response.getName());
            assertEquals("FAC-888", response.getUserCode());
            assertEquals(Role.FACULTY, response.getRole());
        }

        @Test
        @DisplayName("Should reject user creation when userCode is already in use")
        void testCreateUserDuplicateCodeThrowsException() {
            CreateUserRequestDto req = new CreateUserRequestDto();
            req.setUserCode("FAC-888");
            req.setEmail("unique@intellicampus.edu");

            when(userRepository.existsByUserCode("FAC-888")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    userService.createUser(req)
            );
            assertTrue(ex.getMessage().contains("User code already in use"));
        }

        @Test
        @DisplayName("Should filter users by search keyword and role")
        void testSearchAndRoleFiltering() {
            User u1 = new User("STU-001", Role.STUDENT, "Aarav Mehta", "aarav@intellicampus.edu", "hash", csDept);
            User u2 = new User("FAC-001", Role.FACULTY, "Dr. Mehta", "dr.mehta@intellicampus.edu", "hash", csDept);

            when(userRepository.findAll()).thenReturn(List.of(u1, u2));

            // Search "mehta" with STUDENT role filter
            List<UserResponseDto> students = userService.getAllUsers("mehta", Role.STUDENT);
            assertEquals(1, students.size());
            assertEquals("STU-001", students.get(0).getUserCode());

            // Search "mehta" with null role filter returns both
            List<UserResponseDto> all = userService.getAllUsers("mehta", null);
            assertEquals(2, all.size());
        }
    }
}
