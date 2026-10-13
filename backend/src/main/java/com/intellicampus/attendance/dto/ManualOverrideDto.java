package com.intellicampus.attendance.dto;

import com.intellicampus.attendance.model.AttendanceStatus;

public class ManualOverrideDto {
    private AttendanceStatus status;
    private String reason;

    public ManualOverrideDto() {}

    public AttendanceStatus getStatus() { return status; }
    public void setStatus(AttendanceStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
