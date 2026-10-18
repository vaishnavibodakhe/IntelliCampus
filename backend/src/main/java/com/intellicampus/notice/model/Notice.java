package com.intellicampus.notice.model;

import com.intellicampus.user.model.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "notices", indexes = {
    @Index(name = "idx_notices_published_at", columnList = "published_at")
})
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "target_department", length = 100)
    private String targetDepartment = "All Departments";

    @Column(name = "target_year", length = 50)
    private String targetYear = "All Years";

    @Column(name = "target_division", length = 50)
    private String targetDivision = "All Divisions";

    @Column(name = "publisher_role", length = 50)
    private String publisherRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private User publisher;

    @Column
    private LocalDate deadline;

    @Column(name = "published_at")
    private LocalDateTime publishedAt = LocalDateTime.now();

    @Column(name = "has_attachment")
    private Boolean hasAttachment = false;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    public Notice() {}

    public Notice(String title, String body, String targetDepartment, String targetYear, String targetDivision, String publisherRole, User publisher, LocalDate deadline) {
        this.title = title;
        this.body = body;
        this.targetDepartment = targetDepartment != null ? targetDepartment : "All Departments";
        this.targetYear = targetYear != null ? targetYear : "All Years";
        this.targetDivision = targetDivision != null ? targetDivision : "All Divisions";
        this.publisherRole = publisherRole;
        this.publisher = publisher;
        this.deadline = deadline;
        this.publishedAt = LocalDateTime.now();
        this.hasAttachment = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getTargetDepartment() { return targetDepartment; }
    public void setTargetDepartment(String targetDepartment) { this.targetDepartment = targetDepartment; }

    public String getTargetYear() { return targetYear; }
    public void setTargetYear(String targetYear) { this.targetYear = targetYear; }

    public String getTargetDivision() { return targetDivision; }
    public void setTargetDivision(String targetDivision) { this.targetDivision = targetDivision; }

    public String getPublisherRole() { return publisherRole; }
    public void setPublisherRole(String publisherRole) { this.publisherRole = publisherRole; }

    public User getPublisher() { return publisher; }
    public void setPublisher(User publisher) { this.publisher = publisher; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public Boolean getHasAttachment() { return hasAttachment; }
    public void setHasAttachment(Boolean hasAttachment) { this.hasAttachment = hasAttachment; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
}


