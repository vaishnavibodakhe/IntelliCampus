package com.intellicampus.leave.validation;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Validates leave application date intervals, retroactive application limits, and policy compliance.
 * Compliant with IC-SRS-1.0 Section 8.4 and Test Case TC-07.
 */
@Component
public class LeaveDateValidator {

    public static final int MAX_RETROACTIVE_DAYS = 7;
    public static final int MEDICAL_CERTIFICATE_CONSECUTIVE_DAYS_THRESHOLD = 2;

    /**
     * Validates date interval ordering and retroactive submission bounds.
     *
     * @param fromDate Start date of leave
     * @param toDate   End date of leave
     * @throws IllegalArgumentException If dates are invalid or violate policy
     */
    public void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Both leave start date (fromDate) and end date (toDate) are mandatory.");
        }

        // Criterion 1: Validate fromDate <= toDate
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException(
                    String.format("Leave end date (%s) cannot be earlier than start date (%s).", toDate, fromDate)
            );
        }

        // Criterion 2: Leave cannot be applied retroactively past allowed window (7 days)
        LocalDate earliestAllowedRetroactive = LocalDate.now().minusDays(MAX_RETROACTIVE_DAYS);
        if (fromDate.isBefore(earliestAllowedRetroactive)) {
            throw new IllegalArgumentException(
                    String.format("Leave cannot be applied retroactively past the %d-day window (Earliest permissible start date: %s).",
                            MAX_RETROACTIVE_DAYS, earliestAllowedRetroactive)
            );
        }
    }

    /**
     * Enforces leave policy compliance including mandatory medical certificates for prolonged illnesses.
     *
     * @param fromDate       Start date
     * @param toDate         End date
     * @param reason         Application justification
     * @param attachmentName File attachment name (if any)
     * @param attachmentUrl  File download URL (if any)
     */
    public void validatePolicy(LocalDate fromDate, LocalDate toDate, String reason,
                               String attachmentName, String attachmentUrl) {

        validateDateRange(fromDate, toDate);

        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Leave reason is mandatory and must be at least 5 characters long.");
        }

        long daysCount = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        String reasonLower = reason.toLowerCase();

        // Criterion 3: Enforce medical certificate attachment for sick leaves exceeding 2 consecutive days
        boolean isMedicalLeave = reasonLower.contains("medical") ||
                reasonLower.contains("sick") ||
                reasonLower.contains("hospital") ||
                reasonLower.contains("illness") ||
                reasonLower.contains("health") ||
                reasonLower.contains("doctor");

        if (isMedicalLeave && daysCount > MEDICAL_CERTIFICATE_CONSECUTIVE_DAYS_THRESHOLD) {
            boolean hasAttachment = (attachmentName != null && !attachmentName.trim().isEmpty()) ||
                    (attachmentUrl != null && !attachmentUrl.trim().isEmpty());

            if (!hasAttachment) {
                throw new IllegalArgumentException(
                        String.format("Medical leave exceeding %d consecutive days (%d days requested) requires a valid doctor/hospital medical certificate attachment.",
                                MEDICAL_CERTIFICATE_CONSECUTIVE_DAYS_THRESHOLD, daysCount)
                );
            }
        }
    }

    /**
     * Calculates inclusive days count.
     */
    public int calculateDays(LocalDate fromDate, LocalDate toDate) {
        validateDateRange(fromDate, toDate);
        return (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    }
}
