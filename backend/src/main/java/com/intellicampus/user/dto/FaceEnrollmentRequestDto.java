package com.intellicampus.user.dto;

import java.util.List;

public class FaceEnrollmentRequestDto {

    private String token;
    private List<String> frames;
    private String frontFrame;
    private String leftFrame;
    private String rightFrame;
    private Object livenessResult;
    private Boolean liveness;
    private String consentVersion;
    private Long studentId;

    public FaceEnrollmentRequestDto() {}

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<String> getFrames() {
        if (frames == null && frontFrame != null && leftFrame != null && rightFrame != null) {
            return List.of(frontFrame, leftFrame, rightFrame);
        }
        return frames;
    }

    public void setFrames(List<String> frames) {
        this.frames = frames;
    }

    public String getFrontFrame() {
        return frontFrame;
    }

    public void setFrontFrame(String frontFrame) {
        this.frontFrame = frontFrame;
    }

    public String getLeftFrame() {
        return leftFrame;
    }

    public void setLeftFrame(String leftFrame) {
        this.leftFrame = leftFrame;
    }

    public String getRightFrame() {
        return rightFrame;
    }

    public void setRightFrame(String rightFrame) {
        this.rightFrame = rightFrame;
    }

    public Object getLivenessResult() {
        return livenessResult;
    }

    public void setLivenessResult(Object livenessResult) {
        this.livenessResult = livenessResult;
    }

    public Boolean getLiveness() {
        return liveness;
    }

    public void setLiveness(Boolean liveness) {
        this.liveness = liveness;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public void setConsentVersion(String consentVersion) {
        this.consentVersion = consentVersion;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
}
