package com.intellicampus.leave.dto;

import com.intellicampus.leave.model.LeaveStatus;

public class LeaveDecisionDto {
    private Long approverId;
    private boolean approved;
    private LeaveStatus status;
    private String comment;

    public LeaveDecisionDto() {}

    public LeaveDecisionDto(Long approverId, boolean approved, String comment) {
        this.approverId = approverId;
        this.approved = approved;
        this.comment = comment;
    }

    public Long getApproverId() { return approverId; }
    public void setApproverId(Long approverId) { this.approverId = approverId; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
