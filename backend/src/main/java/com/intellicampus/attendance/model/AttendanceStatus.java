package com.intellicampus.attendance.model;

/**
 * Verification state of an individual student record within an attendance session.
 */
public enum AttendanceStatus {
    PRESENT,
    MATCHED,
    REVIEW,
    ABSENT,
    PENDING
}
