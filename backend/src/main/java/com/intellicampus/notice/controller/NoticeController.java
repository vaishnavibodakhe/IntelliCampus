package com.intellicampus.notice.controller;

import com.intellicampus.notice.dto.CreateNoticeRequestDto;
import com.intellicampus.notice.dto.NoticeDto;
import com.intellicampus.notice.service.NoticeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<List<NoticeDto>> getMyTargetedFeed(@RequestParam Long userId) {
        return ResponseEntity.ok(noticeService.getFeedForUser(userId));
    }

    @PostMapping
    public ResponseEntity<NoticeDto> publishNotice(@RequestBody CreateNoticeRequestDto req) {
        NoticeDto created = noticeService.createNotice(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, @RequestParam Long userId) {
        boolean recorded = noticeService.markAsRead(id, userId);
        return recorded ? ResponseEntity.ok().build() : ResponseEntity.noContent().build();
    }
}
