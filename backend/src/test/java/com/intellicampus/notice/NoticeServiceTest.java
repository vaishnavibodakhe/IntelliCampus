package com.intellicampus.notice;

import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.notice.dto.CreateNoticeRequestDto;
import com.intellicampus.notice.dto.NoticeDto;
import com.intellicampus.notice.model.Notice;
import com.intellicampus.notice.model.NoticeReadReceipt;
import com.intellicampus.notice.repository.NoticeReadReceiptRepository;
import com.intellicampus.notice.repository.NoticeRepository;
import com.intellicampus.notice.service.NoticeService;
import com.intellicampus.notice.validation.NoticeAudienceValidator;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit and integration test suite for NoticeService covering audience targeting
 * and student read receipt tracking.
 * Compliant with IC-SRS-1.0 Section 8.5 (ADM-02) and Test Case TC-10.
 */
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;
    @Mock
    private NoticeReadReceiptRepository noticeReadReceiptRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Spy
    private NoticeAudienceValidator noticeAudienceValidator = new NoticeAudienceValidator();

    @InjectMocks
    private NoticeService noticeService;

    private Department csDept;
    private Division teDiv;
    private User studentUser;
    private StudentProfile studentProfile;
    private Notice campusWideNotice;
    private Notice csYear3Notice;

    @BeforeEach
    void setUp() {
        csDept = new Department("CS", "Computer Science");
        ReflectionTestUtils.setField(csDept, "id", 1L);

        teDiv = new Division(csDept, 3, "TE-CS-A");
        ReflectionTestUtils.setField(teDiv, "id", 10L);

        studentUser = new User("STU-100", Role.STUDENT, "Aarav Mehta", "aarav@intellicampus.edu", "pass123", csDept);
        ReflectionTestUtils.setField(studentUser, "id", 200L);

        studentProfile = new StudentProfile(studentUser, "2024-CS-001", teDiv, null);
        ReflectionTestUtils.setField(studentProfile, "id", 300L);

        campusWideNotice = new Notice(
                "Campus Independence Day Holiday",
                "The college campus will remain closed on Friday for national observance.",
                "All Departments",
                "All Years",
                "All Divisions",
                "Principal Office",
                null,
                null
        );
        ReflectionTestUtils.setField(campusWideNotice, "id", 1L);

        csYear3Notice = new Notice(
                "TE Computer Science Mini-Project Synopsis Submission",
                "All Year 3 Computer Science students must upload synopsis before 5:00 PM.",
                "Computer Science",
                "Year 3",
                "All Divisions",
                "HOD Office",
                null,
                LocalDate.now().plusDays(3)
        );
        ReflectionTestUtils.setField(csYear3Notice, "id", 2L);
    }

    @Nested
    @DisplayName("Audience Targeting Tests")
    class AudienceTargetingTests {

        @Test
        @DisplayName("Criterion 2: Student receives targeted notices matching department/year and campus-wide notices")
        void testStudentReceivesTargetedNotices() {
            when(userRepository.findById(200L)).thenReturn(Optional.of(studentUser));
            when(studentProfileRepository.findByUser(studentUser)).thenReturn(Optional.of(studentProfile));

            // Repository query returns matched notices for Computer Science, Year 3, TE-CS-A
            when(noticeRepository.findTargetedNoticesForUser(200L, "Computer Science", "Year 3", "TE-CS-A"))
                    .thenReturn(List.of(campusWideNotice, csYear3Notice));
            when(noticeReadReceiptRepository.countByNotice(any(Notice.class))).thenReturn(5L);

            List<NoticeDto> feed = noticeService.getFeedForUser(200L);

            assertNotNull(feed);
            assertEquals(2, feed.size(), "Feed should contain 2 notices matching student audience");
            assertTrue(feed.stream().anyMatch(n -> n.getTitle().contains("Campus Independence Day")));
            assertTrue(feed.stream().anyMatch(n -> n.getTitle().contains("TE Computer Science")));

            verify(noticeRepository).findTargetedNoticesForUser(200L, "Computer Science", "Year 3", "TE-CS-A");
        }

        @Test
        @DisplayName("Criterion 2: Validates notice audience taxonomy upon notice creation")
        void testCreateNoticeWithValidAudience() {
            CreateNoticeRequestDto req = new CreateNoticeRequestDto();
            req.setTitle("Campus Hackathon Announcement");
            req.setBody("Registration open for intra-college 24h hackathon");
            req.setTargetDepartment("Computer Science");
            req.setTargetYear("Year 3");
            req.setTargetDivision("All Divisions");
            req.setPublisherRole("HOD Office");

            when(noticeRepository.save(any(Notice.class))).thenAnswer(inv -> {
                Notice n = inv.getArgument(0);
                ReflectionTestUtils.setField(n, "id", 10L);
                return n;
            });

            NoticeDto dto = noticeService.createNotice(req);

            assertNotNull(dto);
            assertEquals("Campus Hackathon Announcement", dto.getTitle());
            assertEquals("Computer Science", dto.getTargetDepartment());
            assertEquals("Year 3", dto.getTargetYear());

            verify(noticeAudienceValidator).validateAudience("Computer Science", "Year 3", "All Divisions");
            verify(noticeRepository).save(any(Notice.class));
        }

        @Test
        @DisplayName("Rejects notice creation when target department or year is invalid")
        void testCreateNoticeWithInvalidAudienceThrowsException() {
            CreateNoticeRequestDto req = new CreateNoticeRequestDto();
            req.setTitle("Invalid Notice");
            req.setBody("Content");
            req.setTargetDepartment("Invalid Unknown Dept"); // Non-existent taxonomy
            req.setTargetYear("All Years");

            assertThrows(IllegalArgumentException.class, () ->
                    noticeService.createNotice(req)
            );

            verify(noticeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read Receipt Generation Tests")
    class ReadReceiptTests {

        @Test
        @DisplayName("Criterion 3: Generates read receipt when student accesses notice detail for first time")
        void testFirstTimeReadReceiptGeneration() {
            when(noticeRepository.findById(1L)).thenReturn(Optional.of(campusWideNotice));
            when(userRepository.findById(200L)).thenReturn(Optional.of(studentUser));
            when(noticeReadReceiptRepository.existsByNoticeAndUser(campusWideNotice, studentUser)).thenReturn(false);

            boolean receiptGenerated = noticeService.markAsRead(1L, 200L);

            assertTrue(receiptGenerated, "Read receipt should be generated on first access");
            verify(noticeReadReceiptRepository).save(any(NoticeReadReceipt.class));
        }

        @Test
        @DisplayName("Criterion 3: Does not duplicate read receipt when student re-opens same notice")
        void testDuplicateReadReceiptPrevented() {
            when(noticeRepository.findById(1L)).thenReturn(Optional.of(campusWideNotice));
            when(userRepository.findById(200L)).thenReturn(Optional.of(studentUser));
            when(noticeReadReceiptRepository.existsByNoticeAndUser(campusWideNotice, studentUser)).thenReturn(true);

            boolean receiptGenerated = noticeService.markAsRead(1L, 200L);

            assertFalse(receiptGenerated, "Duplicate read receipt should not be generated");
            verify(noticeReadReceiptRepository, never()).save(any(NoticeReadReceipt.class));
        }

        @Test
        @DisplayName("Returns false when notice or user does not exist")
        void testReadReceiptInvalidNoticeOrUser() {
            when(noticeRepository.findById(999L)).thenReturn(Optional.empty());

            boolean result = noticeService.markAsRead(999L, 200L);

            assertFalse(result);
            verify(noticeReadReceiptRepository, never()).save(any());
        }
    }
}



