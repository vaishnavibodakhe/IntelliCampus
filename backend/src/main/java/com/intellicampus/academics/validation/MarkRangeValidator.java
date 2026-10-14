package com.intellicampus.academics.validation;

import org.springframework.stereotype.Component;

/**
 * Validates grading boundaries and mark correction justifications.
 * Compliant with IC-SRS-1.0 Section 8.3 and Test Case TC-05.
 */
@Component
public class MarkRangeValidator {

    public static final int MIN_CORRECTION_REASON_LENGTH = 15;

    /**
     * Validates that assigned marks fall within the allowable range [0, maxMarks].
     *
     * @param marks    Entered score
     * @param maxMarks Total possible score
     * @throws IllegalArgumentException If marks are negative or exceed maxMarks
     */
    public void validateMarks(Double marks, Double maxMarks) {
        if (maxMarks == null || maxMarks <= 0) {
            throw new IllegalArgumentException("Maximum marks must be a positive number.");
        }
        if (marks == null) {
            throw new IllegalArgumentException("Marks value is mandatory.");
        }
        // Criterion 1: Validate marks >= 0 and marks <= maxMarks
        if (marks < 0) {
            throw new IllegalArgumentException("Marks cannot be negative. Provided: " + marks);
        }
        if (marks > maxMarks) {
            throw new IllegalArgumentException(
                    String.format("Marks (%.1f) exceed maximum score boundary of %.1f.", marks, maxMarks)
            );
        }
    }

    public void validateMarks(Integer marks, Integer maxMarks) {
        validateMarks(marks != null ? marks.doubleValue() : null, maxMarks != null ? maxMarks.doubleValue() : null);
    }

    /**
     * Validates that the justification reason for a mark correction request is provided and sufficient.
     *
     * @param reason Faculty explanation for correction
     * @throws IllegalArgumentException If reason is null, empty, or shorter than 15 characters
     */
    public void validateCorrectionReason(String reason) {
        // Criterion 2: Ensure mark correction reason is mandatory and at least 15 characters long
        if (reason == null || reason.trim().length() < MIN_CORRECTION_REASON_LENGTH) {
            int length = (reason == null) ? 0 : reason.trim().length();
            throw new IllegalArgumentException(
                    String.format("Mark correction justification is mandatory and must be at least %d characters (provided: %d characters).",
                            MIN_CORRECTION_REASON_LENGTH, length)
            );
        }
    }

    /**
     * Validates full proposed mark correction request.
     */
    public void validateCorrection(Double proposedMarks, Double maxMarks, String reason) {
        validateMarks(proposedMarks, maxMarks);
        validateCorrectionReason(reason);
    }

    public void validateCorrection(Integer proposedMarks, Integer maxMarks, String reason) {
        validateCorrection(proposedMarks != null ? proposedMarks.doubleValue() : null, maxMarks != null ? maxMarks.doubleValue() : null, reason);
    }
}
