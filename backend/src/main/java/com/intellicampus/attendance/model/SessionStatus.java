package com.intellicampus.attendance.model;

/**
 * Attendance session state machine phases according to IC-SRS-1.0.
 */
public enum SessionStatus {
    OPEN,
    PROCESSING,
    REVIEW,
    FINALISED,
    EXPIRED
}
