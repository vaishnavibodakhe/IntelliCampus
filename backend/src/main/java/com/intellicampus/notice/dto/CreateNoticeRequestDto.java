package com.intellicampus.notice.dto;

import java.time.LocalDate;

public class CreateNoticeRequestDto {
    private String title;
    private String body;
    private String targetDepartment;
    private String targetYear;
    private String targetDivision;
    private String publisherRole;
    private Long publisherId;
    private LocalDate deadline;
    private Boolean hasAttachment;
    private String fileName;
    private String attachmentUrl;
    private java.util.List<Long> targetStudentIds;

    public CreateNoticeRequestDto() {}

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

    public Long getPublisherId() { return publisherId; }
    public void setPublisherId(Long publisherId) { this.publisherId = publisherId; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }

    public Boolean getHasAttachment() { return hasAttachment; }
    public void setHasAttachment(Boolean hasAttachment) { this.hasAttachment = hasAttachment; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public java.util.List<Long> getTargetStudentIds() { return targetStudentIds; }
    public void setTargetStudentIds(java.util.List<Long> targetStudentIds) { this.targetStudentIds = targetStudentIds; }
}
