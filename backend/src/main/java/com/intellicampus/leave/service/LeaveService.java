package com.intellicampus.leave.service;

import com.intellicampus.leave.dto.LeaveDecisionDto;
import com.intellicampus.leave.dto.LeaveResponseDto;
import com.intellicampus.leave.dto.SubmitLeaveRequestDto;
import com.intellicampus.leave.model.LeaveRequest;
import com.intellicampus.leave.model.LeaveStatus;
import com.intellicampus.leave.repository.LeaveRequestRepository;
import com.intellicampus.leave.validation.LeaveDateValidator;
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
    private final LeaveDateValidator leaveDateValidator;
    private final com.intellicampus.security.util.SecurityUtils securityUtils;

    public LeaveService(LeaveRequestRepository leaveRepository,
                        StudentProfileRepository studentProfileRepository,
                        UserRepository userRepository,
                        LeaveDateValidator leaveDateValidator) {
        this(leaveRepository, studentProfileRepository, userRepository, leaveDateValidator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LeaveService(LeaveRequestRepository leaveRepository,
                        StudentProfileRepository studentProfileRepository,
                        UserRepository userRepository,
                        LeaveDateValidator leaveDateValidator,
                        @org.springframework.beans.factory.annotation.Autowired(required = false) com.intellicampus.security.util.SecurityUtils securityUtils) {
        this.leaveRepository = leaveRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.leaveDateValidator = leaveDateValidator;
        this.securityUtils = securityUtils;
    }

    public LeaveResponseDto submitLeave(SubmitLeaveRequestDto req) {
        leaveDateValidator.validatePolicy(
                req.getFromDate(),
                req.getToDate(),
                req.getReason(),
                req.getAttachmentName(),
                req.getAttachmentUrl()
        );

        if (securityUtils != null && securityUtils.isStudent()) {
            StudentProfile currentStudent = securityUtils.getCurrentStudentProfile().orElse(null);
            if (currentStudent != null) {
                if (req.getStudentId() != null && !req.getStudentId().equals(currentStudent.getId())
                        && (currentStudent.getUser() == null || !req.getStudentId().equals(currentStudent.getUser().getId()))) {
                    throw new org.springframework.security.access.AccessDeniedException("Students can only submit leave requests for themselves");
                }
                req.setStudentId(currentStudent.getId());
            }
        }

        StudentProfile student = studentProfileRepository.findById(req.getStudentId())
                .or(() -> userRepository.findById(req.getStudentId()).flatMap(studentProfileRepository::findByUser))
                .orElseThrow(() -> new IllegalArgumentException("Student not found for id: " + req.getStudentId()));

        if (securityUtils != null) {
            securityUtils.verifyCanAccessStudent(student);
        }

        int daysCount = leaveDateValidator.calculateDays(req.getFromDate(), req.getToDate());

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

        User mentor = null;
        if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            User current = securityUtils.getCurrentUser().get();
            if (current.getRole() == com.intellicampus.user.model.Role.MENTOR) {
                if (leave.getMentor() != null && current.getId() != null && !current.getId().equals(leave.getMentor().getId())) {
                    throw new org.springframework.security.access.AccessDeniedException("Mentors may only review leave requests for their assigned mentees");
                }
                mentor = current;
            } else if (current.getRole() == com.intellicampus.user.model.Role.PRINCIPAL) {
                mentor = current;
            }
        }

        if (mentor != null) {
            leave.setMentor(mentor);
        } else if (decision.getApproverId() != null) {
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

        User hod = null;
        if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            User current = securityUtils.getCurrentUser().get();
            if (current.getRole() == com.intellicampus.user.model.Role.HOD) {
                if (current.getDepartment() != null
                        && leave.getStudent() != null
                        && leave.getStudent().getDivision() != null
                        && leave.getStudent().getDivision().getDepartment() != null) {
                    if (!current.getDepartment().getId().equals(leave.getStudent().getDivision().getDepartment().getId())) {
                        throw new org.springframework.security.access.AccessDeniedException("HOD may only approve leave requests for students within their own department");
                    }
                }
                hod = current;
            } else if (current.getRole() == com.intellicampus.user.model.Role.PRINCIPAL) {
                hod = current;
            }
        }

        if (hod != null) {
            leave.setHod(hod);
        } else if (decision.getApproverId() != null) {
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

        if (securityUtils != null) {
            securityUtils.verifyCanAccessStudent(student);
        }

        return leaveRepository.findByStudentOrderBySubmittedAtDesc(student).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getPendingMentorLeaves(Long mentorId) {
        if (securityUtils != null && securityUtils.isMentor()) {
            Long currentId = securityUtils.getCurrentUserId();
            if (currentId != null && !currentId.equals(mentorId)) {
                throw new org.springframework.security.access.AccessDeniedException("Mentors can only view their own pending leave queue");
            }
        }

        User mentor = userRepository.findById(mentorId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found for id: " + mentorId));

        return leaveRepository.findByMentorAndMentorStatus(mentor, LeaveStatus.SUBMITTED).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveResponseDto> getPendingHodLeaves() {
        User currentHod = (securityUtils != null) ? securityUtils.getCurrentUser().orElse(null) : null;
        return leaveRepository.findByHodStatus(LeaveStatus.SUBMITTED).stream()
                .filter(l -> l.getMentorStatus() == LeaveStatus.RECOMMENDED)
                .filter(l -> {
                    if (currentHod != null && currentHod.getRole() == com.intellicampus.user.model.Role.HOD && currentHod.getDepartment() != null) {
                        return l.getStudent() != null && l.getStudent().getDivision() != null
                                && l.getStudent().getDivision().getDepartment() != null
                                && currentHod.getDepartment().getId().equals(l.getStudent().getDivision().getDepartment().getId());
                    }
                    return true;
                })
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
        LeaveRequest leave = leaveRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + id));
        if (securityUtils != null) {
            securityUtils.verifyCanAccessStudent(leave.getStudent());
        }
        return mapToDto(leave);
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
