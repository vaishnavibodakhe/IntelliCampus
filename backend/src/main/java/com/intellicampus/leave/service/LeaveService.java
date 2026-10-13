package com.intellicampus.leave.service;

import com.intellicampus.leave.dto.LeaveDecisionDto;
import com.intellicampus.leave.dto.LeaveResponseDto;
import com.intellicampus.leave.dto.SubmitLeaveRequestDto;
import com.intellicampus.leave.model.LeaveRequest;
import com.intellicampus.leave.model.LeaveStatus;
import com.intellicampus.leave.repository.LeaveRequestRepository;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class LeaveService {

    private final LeaveRequestRepository leaveRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;

    public LeaveService(LeaveRequestRepository leaveRepository,
                        StudentProfileRepository studentProfileRepository,
                        UserRepository userRepository) {
        this.leaveRepository = leaveRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
    }

    public LeaveResponseDto submitLeave(SubmitLeaveRequestDto req) {
        if (req.getFromDate() == null || req.getToDate() == null) {
            throw new IllegalArgumentException("Both fromDate and toDate are required.");
        }
        if (req.getToDate().isBefore(req.getFromDate())) {
            throw new IllegalArgumentException("To-date cannot be before from-date.");
        }
        if (req.getReason() == null || req.getReason().trim().length() < 5) {
            throw new IllegalArgumentException("Leave reason must be at least 5 characters long.");
        }

        StudentProfile student = studentProfileRepository.findById(req.getStudentId())
                .or(() -> userRepository.findById(req.getStudentId()).flatMap(studentProfileRepository::findByUser))
                .orElseThrow(() -> new IllegalArgumentException("Student not found for id: " + req.getStudentId()));

        int daysCount = (int) ChronoUnit.DAYS.between(req.getFromDate(), req.getToDate()) + 1;

        LeaveRequest leave = new LeaveRequest(
                student,
                req.getFromDate(),
                req.getToDate(),
                daysCount,
                req.getReason(),
                req.getAttachmentName(),
                req.getAttachmentUrl(),
                student.getMentor()
        );

        LeaveRequest saved = leaveRepository.save(leave);
        return mapToDto(saved);
    }

    public LeaveResponseDto processMentorReview(Long leaveId, LeaveDecisionDto decision) {
        LeaveRequest leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveId));

        if (decision.getApproverId() != null) {
            userRepository.findById(decision.getApproverId()).ifPresent(leave::setMentor);
        }

        leave.setMentorComment(decision.getComment());

        if (decision.isApproved() || decision.getStatus() == LeaveStatus.RECOMMENDED || decision.getStatus() == LeaveStatus.APPROVED) {
            leave.setMentorStatus(LeaveStatus.RECOMMENDED);
            leave.setFinalStatus(LeaveStatus.RECOMMENDED);
        } else {
            leave.setMentorStatus(LeaveStatus.MENTOR_REJECTED);
            leave.setFinalStatus(LeaveStatus.MENTOR_REJECTED);
            leave.setDecidedAt(LocalDateTime.now());
        }

        LeaveRequest saved = leaveRepository.save(leave);
        return mapToDto(saved);
    }

    public LeaveResponseDto processHodReview(Long leaveId, LeaveDecisionDto decision) {
        LeaveRequest leave = leaveRepository.findById(leaveId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveId));

        if (decision.getApproverId() != null) {
            userRepository.findById(decision.getApproverId()).ifPresent(leave::setHod);
        }

        leave.setHodComment(decision.getComment());
        leave.setDecidedAt(LocalDateTime.now());

        if (decision.isApproved() || decision.getStatus() == LeaveStatus.APPROVED) {
            leave.setHodStatus(LeaveStatus.APPROVED);
            leave.setFinalStatus(LeaveStatus.APPROVED);
        } else {
            leave.setHodStatus(LeaveStatus.HOD_REJECTED);
            leave.setFinalStatus(LeaveStatus.HOD_REJECTED);
        }

        LeaveRequest saved = leaveRepository.save(leave);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getStudentLeaves(Long studentId) {
        StudentProfile student = studentProfileRepository.findById(studentId)
                .or(() -> userRepository.findById(studentId).flatMap(studentProfileRepository::findByUser))
                .orElseThrow(() -> new IllegalArgumentException("Student not found for id: " + studentId));

        return leaveRepository.findByStudentOrderBySubmittedAtDesc(student).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getPendingMentorLeaves(Long mentorId) {
        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found for id: " + mentorId));

        return leaveRepository.findByMentorAndMentorStatus(mentor, LeaveStatus.SUBMITTED).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getPendingHodLeaves() {
        return leaveRepository.findByHodStatus(LeaveStatus.SUBMITTED).stream()
                .filter(l -> l.getMentorStatus() == LeaveStatus.RECOMMENDED)
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getAllLeaves() {
        return leaveRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public LeaveResponseDto getLeaveById(Long id) {
        return leaveRepository.findById(id)
                .map(this::mapToDto)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + id));
    }

    private LeaveResponseDto mapToDto(LeaveRequest l) {
        LeaveResponseDto dto = new LeaveResponseDto();
        dto.setId(l.getId());
        dto.setStudentId(l.getStudent().getId());
        dto.setStudentName(l.getStudent().getUser().getName());
        dto.setRollNo(l.getStudent().getRollNo());
        if (l.getStudent().getDivision() != null && l.getStudent().getDivision().getDepartment() != null) {
            dto.setDepartmentName(l.getStudent().getDivision().getDepartment().getName());
        }
        dto.setFromDate(l.getFromDate());
        dto.setToDate(l.getToDate());
        dto.setDaysCount(l.getDaysCount());
        dto.setReason(l.getReason());
        dto.setAttachmentName(l.getAttachmentName());
        dto.setAttachmentUrl(l.getAttachmentUrl());

        if (l.getMentor() != null) {
            dto.setMentorId(l.getMentor().getId());
            dto.setMentorName(l.getMentor().getName());
        }
        dto.setMentorStatus(l.getMentorStatus());
        dto.setMentorComment(l.getMentorComment());

        if (l.getHod() != null) {
            dto.setHodId(l.getHod().getId());
            dto.setHodName(l.getHod().getName());
        }
        dto.setHodStatus(l.getHodStatus());
        dto.setHodComment(l.getHodComment());

        dto.setFinalStatus(l.getFinalStatus());
        dto.setSubmittedAt(l.getSubmittedAt());
        dto.setDecidedAt(l.getDecidedAt());
        return dto;
    }
}
