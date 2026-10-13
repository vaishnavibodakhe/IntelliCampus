package com.intellicampus.notice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class NoticeDto {
    private Long id;
    private String title;
    private String body;
    private String targetDepartment;
    private String targetYear;
    private String targetDivision;
    private String publisherRole;
    private String publisherName;
    private LocalDate deadline;
    private LocalDateTime publishedAt;
    private long readsCount;
    private Boolean hasAttachment;
    private String fileName;
    private String attachmentUrl;

    public NoticeDto() {}

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

    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public long getReadsCount() { return readsCount; }
    public void setReadsCount(long readsCount) { this.readsCount = readsCount; }

    public Boolean getHasAttachment() { return hasAttachment; }
    public void setHasAttachment(Boolean hasAttachment) { this.hasAttachment = hasAttachment; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }
}
