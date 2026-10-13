package com.intellicampus.notice.model;

import com.intellicampus.user.model.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notice_read_receipts", uniqueConstraints = {
    @UniqueConstraint(name = "uq_notice_user", columnNames = {"notice_id", "user_id"})
})
public class NoticeReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id", nullable = false)
    private Notice notice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    public NoticeReadReceipt() {}

    public NoticeReadReceipt(Notice notice, User user) {
        this.notice = notice;
        this.user = user;
        this.readAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Notice getNotice() { return notice; }
    public void setNotice(Notice notice) { this.notice = notice; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }
}
