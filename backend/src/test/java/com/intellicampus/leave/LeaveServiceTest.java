package com.intellicampus.leave;

import com.intellicampus.leave.dto.LeaveDecisionDto;
import com.intellicampus.leave.dto.LeaveResponseDto;
import com.intellicampus.leave.dto.SubmitLeaveRequestDto;
import com.intellicampus.leave.model.LeaveRequest;
import com.intellicampus.leave.model.LeaveStatus;
import com.intellicampus.leave.repository.LeaveRequestRepository;
import com.intellicampus.leave.service.LeaveService;
import com.intellicampus.leave.validation.LeaveDateValidator;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for LeaveService covering the multi-tier leave approval lifecycle:
 * SUBMITTED -> MENTOR_RECOMMENDED -> HOD APPROVED / REJECTED.
 * Compliant with IC-SRS-1.0 Section 8.4 and Test Case TC-06.
 */
@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRequestRepository leaveRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LeaveDateValidator leaveDateValidator;

    @InjectMocks
    private LeaveService leaveService;

    private User studentUser;
    private User mentorUser;
    private User hodUser;
    private StudentProfile student;

    @BeforeEach
    void setUp() {
        studentUser = new User("Ananya Desai", "ananya@intellicampus.edu", "pass", Role.STUDENT, "STU-301");
        ReflectionTestUtils.setField(studentUser, "id", 1L);

        mentorUser = new User("Prof. R. Mehta", "mehta@intellicampus.edu", "pass", Role.FACULTY, "FAC-201");
        ReflectionTestUtils.setField(mentorUser, "id", 2L);

        hodUser = new User("Dr. K. Sharma", "hod.cs@intellicampus.edu", "pass", Role.HOD, "HOD-001");
        ReflectionTestUtils.setField(hodUser, "id", 3L);

        student = new StudentProfile(studentUser, "2024-CS-099");
        student.setMentor(mentorUser);
        ReflectionTestUtils.setField(student, "id", 10L);
    }

    @Nested
    @DisplayName("Leave Submission Tests (Initial State: SUBMITTED)")
    class LeaveSubmissionTests {

        @Test
        @DisplayName("Should submit leave with initial status SUBMITTED")
        void testSubmitLeave() {
            LocalDate from = LocalDate.now().plusDays(2);
            LocalDate to = LocalDate.now().plusDays(4);

            SubmitLeaveRequestDto req = new SubmitLeaveRequestDto();
            req.setStudentId(10L);
            req.setFromDate(from);
            req.setToDate(to);
            req.setReason("Attending state level technical symposium");

            when(studentProfileRepository.findById(10L)).thenReturn(Optional.of(student));
            when(leaveDateValidator.calculateDays(from, to)).thenReturn(3);
            when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
                LeaveRequest l = inv.getArgument(0);
                ReflectionTestUtils.setField(l, "id", 501L);
                return l;
            });

            LeaveResponseDto response = leaveService.submitLeave(req);

            assertNotNull(response);
            assertEquals(501L, response.getId());
            assertEquals(LeaveStatus.SUBMITTED, response.getFinalStatus());
            assertEquals(LeaveStatus.SUBMITTED, response.getMentorStatus());
            assertEquals(LeaveStatus.SUBMITTED, response.getHodStatus());
            assertEquals(3, response.getDaysCount());
            assertEquals("Ananya Desai", response.getStudentName());

            verify(leaveDateValidator).validatePolicy(from, to, req.getReason(), null, null);
            verify(leaveRepository).save(any(LeaveRequest.class));
        }
    }

    @Nested
    @DisplayName("Multi-Tier Approval Lifecycle (SUBMITTED -> RECOMMENDED -> APPROVED/REJECTED)")
    class MultiTierApprovalLifecycleTests {

        @Test
        @DisplayName("Criterion 3: Full lifecycle transition SUBMITTED -> MENTOR RECOMMENDED -> HOD APPROVED")
        void testFullApprovalLifecycle() {
            LocalDate from = LocalDate.now().plusDays(1);
            LocalDate to = LocalDate.now().plusDays(3);
            LeaveRequest leave = new LeaveRequest(student, from, to, 3, "Medical rest after fever", "med_cert.pdf", "https://docs.edu/123", mentorUser);
            ReflectionTestUtils.setField(leave, "id", 501L);

            // 1. Initial State: SUBMITTED
            assertEquals(LeaveStatus.SUBMITTED, leave.getMentorStatus());
            assertEquals(LeaveStatus.SUBMITTED, leave.getHodStatus());
            assertEquals(LeaveStatus.SUBMITTED, leave.getFinalStatus());

            // 2. Mentor Review: Recommends the leave request
            LeaveDecisionDto mentorDecision = new LeaveDecisionDto();
            mentorDecision.setApproved(true);
            mentorDecision.setStatus(LeaveStatus.RECOMMENDED);
            mentorDecision.setApproverId(2L);
            mentorDecision.setComment("Medical certificate verified. Recommended.");

            when(leaveRepository.findById(501L)).thenReturn(Optional.of(leave));
            when(userRepository.findById(2L)).thenReturn(Optional.of(mentorUser));
            when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            LeaveResponseDto mentorResult = leaveService.processMentorReview(501L, mentorDecision);

            assertNotNull(mentorResult);
            assertEquals(LeaveStatus.RECOMMENDED, mentorResult.getMentorStatus(), "Mentor status should transition to RECOMMENDED");
            assertEquals(LeaveStatus.RECOMMENDED, mentorResult.getFinalStatus(), "Final status should transition to RECOMMENDED");
            assertEquals("Medical certificate verified. Recommended.", mentorResult.getMentorComment());

            // 3. HOD Review: Approves the recommended leave request
            LeaveDecisionDto hodDecision = new LeaveDecisionDto();
            hodDecision.setApproved(true);
            hodDecision.setStatus(LeaveStatus.APPROVED);
            hodDecision.setApproverId(3L);
            hodDecision.setComment("Approved by Department Head.");

            when(userRepository.findById(3L)).thenReturn(Optional.of(hodUser));

            LeaveResponseDto hodResult = leaveService.processHodReview(501L, hodDecision);

            assertNotNull(hodResult);
            assertEquals(LeaveStatus.APPROVED, hodResult.getHodStatus(), "HOD status should transition to APPROVED");
            assertEquals(LeaveStatus.APPROVED, hodResult.getFinalStatus(), "Final status should transition to APPROVED");
            assertNotNull(hodResult.getDecidedAt(), "Decided timestamp should be recorded");
        }

        @Test
        @DisplayName("Criterion 3: Mentor Rejection stops workflow at MENTOR_REJECTED")
        void testMentorRejectionTransition() {
            LeaveRequest leave = new LeaveRequest(student, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), 2, "Personal errand", null, null, mentorUser);
            ReflectionTestUtils.setField(leave, "id", 502L);

            LeaveDecisionDto mentorDecision = new LeaveDecisionDto();
            mentorDecision.setApproved(false);
            mentorDecision.setApproverId(2L);
            mentorDecision.setComment("Upcoming lab exam on these dates; leave cannot be granted.");

            when(leaveRepository.findById(502L)).thenReturn(Optional.of(leave));
            when(userRepository.findById(2L)).thenReturn(Optional.of(mentorUser));
            when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            LeaveResponseDto result = leaveService.processMentorReview(502L, mentorDecision);

            assertEquals(LeaveStatus.MENTOR_REJECTED, result.getMentorStatus());
            assertEquals(LeaveStatus.MENTOR_REJECTED, result.getFinalStatus());
            assertEquals("Upcoming lab exam on these dates; leave cannot be granted.", result.getMentorComment());
            assertNotNull(result.getDecidedAt());
        }

        @Test
        @DisplayName("Criterion 3: HOD Rejection after Mentor Recommendation transitions to HOD_REJECTED")
        void testHodRejectionAfterMentorRecommendation() {
            LeaveRequest leave = new LeaveRequest(student, LocalDate.now().plusDays(1), LocalDate.now().plusDays(4), 4, "Family function", null, null, mentorUser);
            leave.setMentorStatus(LeaveStatus.RECOMMENDED);
            leave.setFinalStatus(LeaveStatus.RECOMMENDED);
            ReflectionTestUtils.setField(leave, "id", 503L);

            LeaveDecisionDto hodDecision = new LeaveDecisionDto();
            hodDecision.setApproved(false);
            hodDecision.setApproverId(3L);
            hodDecision.setComment("Attendance threshold is below 75%; rejected by HOD.");

            when(leaveRepository.findById(503L)).thenReturn(Optional.of(leave));
            when(userRepository.findById(3L)).thenReturn(Optional.of(hodUser));
            when(leaveRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> inv.getArgument(0));

            LeaveResponseDto result = leaveService.processHodReview(503L, hodDecision);

            assertEquals(LeaveStatus.HOD_REJECTED, result.getHodStatus());
            assertEquals(LeaveStatus.HOD_REJECTED, result.getFinalStatus());
            assertEquals("Attendance threshold is below 75%; rejected by HOD.", result.getHodComment());
        }

        @Test
        @DisplayName("HOD pending leaves only include requests that have been RECOMMENDED by mentor")
        void testHodPendingFilterOnlyRecommended() {
            LeaveRequest recommendedLeave = new LeaveRequest(student, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), 3, "Hackathon", null, null, mentorUser);
            recommendedLeave.setMentorStatus(LeaveStatus.RECOMMENDED);
            recommendedLeave.setHodStatus(LeaveStatus.SUBMITTED);

            LeaveRequest unreviewedLeave = new LeaveRequest(student, LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), 2, "Illness", null, null, mentorUser);
            unreviewedLeave.setMentorStatus(LeaveStatus.SUBMITTED);
            unreviewedLeave.setHodStatus(LeaveStatus.SUBMITTED);

            when(leaveRepository.findByHodStatus(LeaveStatus.SUBMITTED))
                    .thenReturn(List.of(recommendedLeave, unreviewedLeave));

            List<LeaveResponseDto> pendingHod = leaveService.getPendingHodLeaves();

            assertEquals(1, pendingHod.size(), "HOD queue should filter out leaves not yet recommended by mentor");
            assertEquals(LeaveStatus.RECOMMENDED, pendingHod.get(0).getMentorStatus());
        }
    }
}
