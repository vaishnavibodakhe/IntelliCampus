package com.intellicampus.notice.service;

import com.intellicampus.notice.dto.CreateNoticeRequestDto;
import com.intellicampus.notice.dto.NoticeDto;
import com.intellicampus.notice.model.Notice;
import com.intellicampus.notice.model.NoticeReadReceipt;
import com.intellicampus.notice.repository.NoticeReadReceiptRepository;
import com.intellicampus.notice.repository.NoticeRepository;
import com.intellicampus.notice.validation.NoticeAudienceValidator;
import com.intellicampus.user.model.StudentProfile;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.StudentProfileRepository;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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

        return noticeRepository.findTargetedNotices(dept, year, division).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public NoticeDto createNotice(CreateNoticeRequestDto req) {
        noticeAudienceValidator.validateAudience(
                req.getTargetDepartment(),
                req.getTargetYear(),
                req.getTargetDivision()
        );

        User publisher = null;
        String publisherRole = req.getPublisherRole();
        if (securityUtils != null && securityUtils.getCurrentUser().isPresent()) {
            publisher = securityUtils.getCurrentUser().get();
            publisherRole = publisher.getRole().name();
        }
        if (publisher == null && req.getPublisherId() != null) {
            publisher = userRepository.findById(req.getPublisherId()).orElse(null);
        }

        Notice notice = new Notice(
                req.getTitle(),
                req.getBody(),
                req.getTargetDepartment(),
                req.getTargetYear(),
                req.getTargetDivision(),
                publisherRole != null ? publisherRole : "Admin Office",
                publisher,
                req.getDeadline()
        );

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

    private NoticeDto mapToDto(Notice n) {
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
        return dto;
    }
}
