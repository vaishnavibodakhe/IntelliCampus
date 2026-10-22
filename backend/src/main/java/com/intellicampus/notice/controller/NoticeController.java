package com.intellicampus.notice.controller;

import com.intellicampus.notice.dto.CreateNoticeRequestDto;
import com.intellicampus.notice.dto.NoticeDto;
import com.intellicampus.notice.service.NoticeService;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@CrossOrigin(origins = "*")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping
    public ResponseEntity<List<NoticeDto>> listAllNotices() {
        return ResponseEntity.ok(noticeService.getAllNotices());
    }

    @GetMapping("/my-feed")
    public ResponseEntity<List<NoticeDto>> getMyTargetedFeed(
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        Long effectiveUserId = resolveUserId(userId, authentication);
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(noticeService.getFeedForUser(effectiveUserId));
    }

    @PostMapping
    public ResponseEntity<NoticeDto> publishNotice(@RequestBody CreateNoticeRequestDto req) {
        NoticeDto created = noticeService.createNotice(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        Long effectiveUserId = resolveUserId(userId, authentication);
        if (effectiveUserId == null) {
            return ResponseEntity.badRequest().build();
        }
        boolean recorded = noticeService.markAsRead(id, effectiveUserId);
        return recorded ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }

    private Long resolveUserId(Long requestedUserId, Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            if (user.getRole() == Role.STUDENT) {
                return user.getId();
            }
            return requestedUserId != null ? requestedUserId : user.getId();
        }
        return requestedUserId;
    }
}
