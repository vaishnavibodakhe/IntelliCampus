package com.intellicampus.notice.service;

import com.intellicampus.notice.dto.CreateNoticeRequestDto;
import com.intellicampus.notice.dto.NoticeDto;
import com.intellicampus.notice.model.Notice;
import com.intellicampus.notice.model.NoticeReadReceipt;
import com.intellicampus.notice.repository.NoticeReadReceiptRepository;
import com.intellicampus.notice.repository.NoticeRepository;
import com.intellicampus.notice.validation.NoticeAudienceValidator;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final NoticeReadReceiptRepository noticeReadReceiptRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final NoticeAudienceValidator noticeAudienceValidator;
    private final com.intellicampus.security.util.SecurityUtils securityUtils;

    public NoticeService(NoticeRepository noticeRepository,
                         NoticeReadReceiptRepository noticeReadReceiptRepository,
                         UserRepository userRepository,
                         StudentProfileRepository studentProfileRepository,
                         NoticeAudienceValidator noticeAudienceValidator) {
        this(noticeRepository, noticeReadReceiptRepository, userRepository, studentProfileRepository, noticeAudienceValidator, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NoticeService(NoticeRepository noticeRepository,
                         NoticeReadReceiptRepository noticeReadReceiptRepository,
                         UserRepository userRepository,
                         StudentProfileRepository studentProfileRepository,
                         NoticeAudienceValidator noticeAudienceValidator,
                         @org.springframework.beans.factory.annotation.Autowired(required = false) com.intellicampus.security.util.SecurityUtils securityUtils) {
        this.noticeRepository = noticeRepository;
        this.noticeReadReceiptRepository = noticeReadReceiptRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.noticeAudienceValidator = noticeAudienceValidator;
        this.securityUtils = securityUtils;
    }

    @Transactional(readOnly = true)
    public List<NoticeDto> getAllNotices() {
        return noticeRepository.findAllByOrderByPublishedAtDesc().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<NoticeDto> getFeedForUser(Long userId) {
        if (userId == null && securityUtils != null) {
            userId = securityUtils.getCurrentUserId();
        } else if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            userId = securityUtils.getCurrentUserId();
        }

        if (userId == null) {
            return getAllNotices();
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return getAllNotices();
        }

        User user = userOpt.get();
        if (user.getRole() != Role.STUDENT) {
            // For staff roles, return all notices read-only
            return noticeRepository.findAllByOrderByPublishedAtDesc().stream()
                    .map(n -> mapToDto(n, user))
                    .collect(Collectors.toList());
        }

        String dept = user.getDepartment() != null ? user.getDepartment().getName() : "All Departments";
        String year = "All Years";
        String division = "All Divisions";

        Optional<StudentProfile> profileOpt = studentProfileRepository.findByUser(user);
        if (profileOpt.isPresent()) {
            StudentProfile profile = profileOpt.get();
            if (profile.getDivision() != null) {
                division = profile.getDivision().getName();
                year = "Year " + profile.getDivision().getYear();
            }
        }

        return noticeRepository.findTargetedNoticesForUser(userId, dept, year, division).stream()
                .map(n -> mapToDto(n, user))
                .collect(Collectors.toList());
    }

    public NoticeDto createNotice(CreateNoticeRequestDto req) {
        // 1. Validate mandatory content
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Notice title cannot be empty");
        }
        if (req.getBody() == null || req.getBody().trim().isEmpty()) {
            throw new IllegalArgumentException("Notice body cannot be empty");
        }

        // 2. Validate and normalize publisherRole
        if (req.getPublisherRole() == null || req.getPublisherRole().trim().isEmpty()) {
            throw new IllegalArgumentException("Publisher role is required and must be one of: PRINCIPAL, HOD, FACULTY, MENTOR");
        }
        String rawRole = req.getPublisherRole().trim().toUpperCase();
        String normalizedRole;
        if (rawRole.equals("PRINCIPAL") || rawRole.equals("ADMIN") || rawRole.equals("ADMINISTRATOR") || rawRole.equals("ADMIN OFFICE") || rawRole.equals("PRINCIPAL OFFICE")) {
            normalizedRole = "PRINCIPAL";
        } else if (rawRole.equals("HOD") || rawRole.equals("HOD OFFICE") || rawRole.equals("HEAD OF DEPARTMENT")) {
            normalizedRole = "HOD";
        } else if (rawRole.equals("FACULTY") || rawRole.equals("PROFESSOR") || rawRole.equals("TEACHER")) {
            normalizedRole = "FACULTY";
        } else if (rawRole.equals("MENTOR") || rawRole.equals("FACULTY MENTOR")) {
            normalizedRole = "MENTOR";
        } else {
            throw new IllegalArgumentException("Only publisherRole in PRINCIPAL, HOD, FACULTY, MENTOR may publish; rejected: " + req.getPublisherRole());
        }

        // 3. Resolve publisher user if provided
        User publisher = null;
        if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            publisher = securityUtils.getCurrentUser().get();
            normalizedRole = publisher.getRole().name();
        }
        if (publisher == null && req.getPublisherId() != null) {
            publisher = userRepository.findById(req.getPublisherId()).orElse(null);
            if (publisher != null && (publisher.getRole() == Role.STUDENT || publisher.getRole() == Role.PUBLIC)) {
                throw new IllegalArgumentException("User with role " + publisher.getRole() + " is not authorized to publish notices.");
            }
        }

        // 4. Mentor mentee targeting constraint:
        // Mentor may target only their own mentees if a mentor relation exists; otherwise allow any student
        if ("MENTOR".equals(normalizedRole) && publisher != null) {
            List<StudentProfile> mentees = studentProfileRepository.findByMentor(publisher);
            if (mentees != null && !mentees.isEmpty() && req.getTargetStudentIds() != null && !req.getTargetStudentIds().isEmpty()) {
                Set<Long> menteeUserIds = mentees.stream()
                        .filter(sp -> sp.getUser() != null)
                        .map(sp -> sp.getUser().getId())
                        .collect(Collectors.toSet());
                for (Long targetStudentId : req.getTargetStudentIds()) {
                    if (!menteeUserIds.contains(targetStudentId)) {
                        throw new IllegalArgumentException("Mentor may target only their own mentees. Student user ID " + targetStudentId + " is not an assigned mentee.");
                    }
                }
            }
        }

        // 5. Audience taxonomy validation and normalization
        String targetDept = normalizeDepartment(req.getTargetDepartment());
        String targetYear = normalizeYear(req.getTargetYear());
        String targetDiv = normalizeDivision(req.getTargetDivision());

        boolean hasIndividualTargets = req.getTargetStudentIds() != null && !req.getTargetStudentIds().isEmpty();
        if (!hasIndividualTargets) {
            // Group notice: audience parameters are mandatory to match institutional taxonomy
            noticeAudienceValidator.validateAudience(targetDept, targetYear, targetDiv);
        } else {
            // If individual notice supplied specific group filters, validate taxonomy as well
            if (req.getTargetDepartment() != null || req.getTargetYear() != null || req.getTargetDivision() != null) {
                noticeAudienceValidator.validateAudience(targetDept, targetYear, targetDiv);
            }
        }

        Notice notice = new Notice(
                req.getTitle().trim(),
                req.getBody().trim(),
                targetDept,
                targetYear,
                targetDiv,
                normalizedRole,
                publisher,
                req.getDeadline()
        );

        if (hasIndividualTargets) {
            notice.setTargetStudentIds(new HashSet<>(req.getTargetStudentIds()));
        }

        notice.setHasAttachment(req.getHasAttachment() != null && req.getHasAttachment());
        notice.setFileName(req.getFileName());
        notice.setAttachmentUrl(req.getAttachmentUrl());

        Notice saved = noticeRepository.save(notice);
        return mapToDto(saved);
    }

    public NoticeDto updateNotice(Long id, CreateNoticeRequestDto req) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notice not found with id: " + id));

        notice.setTitle(req.getTitle());
        notice.setBody(req.getBody());
        notice.setTargetDepartment(req.getTargetDepartment());
        notice.setTargetYear(req.getTargetYear());
        notice.setTargetDivision(req.getTargetDivision());
        if (req.getDeadline() != null) {
            notice.setDeadline(req.getDeadline());
        }
        if (req.getHasAttachment() != null) {
            notice.setHasAttachment(req.getHasAttachment());
            notice.setFileName(req.getFileName());
            notice.setAttachmentUrl(req.getAttachmentUrl());
        }

        Notice saved = noticeRepository.save(notice);
        return mapToDto(saved);
    }

    public void deleteNotice(Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notice not found with id: " + id));
        noticeRepository.delete(notice);
    }

    public boolean markAsRead(Long noticeId, Long userId) {
        if (userId == null && securityUtils != null) {
            userId = securityUtils.getCurrentUserId();
        } else if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            userId = securityUtils.getCurrentUserId();
        }
        if (userId == null) return false;

        Optional<Notice> noticeOpt = noticeRepository.findById(noticeId);
        Optional<User> userOpt = userRepository.findById(userId);

        if (noticeOpt.isPresent() && userOpt.isPresent()) {
            Notice notice = noticeOpt.get();
            User user = userOpt.get();

            if (!noticeReadReceiptRepository.existsByNoticeAndUser(notice, user)) {
                NoticeReadReceipt receipt = new NoticeReadReceipt(notice, user);
                noticeReadReceiptRepository.save(receipt);
                return true;
            }
        }
        return false;
    }

    private String normalizeDepartment(String dept) {
        if (dept == null || dept.trim().isEmpty() || dept.trim().equalsIgnoreCase("all") || dept.trim().equalsIgnoreCase("all departments")) {
            return "All Departments";
        }
        return dept.trim();
    }

    private String normalizeYear(String year) {
        if (year == null || year.trim().isEmpty() || year.trim().equalsIgnoreCase("all") || year.trim().equalsIgnoreCase("all years")) {
            return "All Years";
        }
        return year.trim();
    }

    private String normalizeDivision(String division) {
        if (division == null || division.trim().isEmpty() || division.trim().equalsIgnoreCase("all") || division.trim().equalsIgnoreCase("all divisions")) {
            return "All Divisions";
        }
        String trimmed = division.trim();
        if (trimmed.equalsIgnoreCase("division a") || trimmed.equalsIgnoreCase("div a")) {
            return "Div A";
        }
        if (trimmed.equalsIgnoreCase("division b") || trimmed.equalsIgnoreCase("div b")) {
            return "Div B";
        }
        if (trimmed.equalsIgnoreCase("division c") || trimmed.equalsIgnoreCase("div c")) {
            return "Div C";
        }
        return trimmed;
    }

    private NoticeDto mapToDto(Notice n) {
        return mapToDto(n, null);
    }

    private NoticeDto mapToDto(Notice n, User currentUser) {
        NoticeDto dto = new NoticeDto();
        dto.setId(n.getId());
        dto.setTitle(n.getTitle());
        dto.setBody(n.getBody());
        dto.setTargetDepartment(n.getTargetDepartment());
        dto.setTargetYear(n.getTargetYear());
        dto.setTargetDivision(n.getTargetDivision());
        dto.setPublisherRole(n.getPublisherRole());
        dto.setPublisherName(n.getPublisher() != null ? n.getPublisher().getName() : n.getPublisherRole());
        dto.setDeadline(n.getDeadline());
        dto.setPublishedAt(n.getPublishedAt());
        dto.setReadsCount(noticeReadReceiptRepository.countByNotice(n));
        dto.setHasAttachment(n.getHasAttachment());
        dto.setFileName(n.getFileName());
        dto.setAttachmentUrl(n.getAttachmentUrl());
        dto.setTargetStudentIds(n.getTargetStudentIds() != null ? new ArrayList<>(n.getTargetStudentIds()) : Collections.emptyList());
        if (currentUser != null) {
            dto.setRead(noticeReadReceiptRepository.existsByNoticeAndUser(n, currentUser));
        } else {
            dto.setRead(false);
        }
        return dto;
    }
}
