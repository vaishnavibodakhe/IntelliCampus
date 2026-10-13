package com.intellicampus.academics.dto;

public class CorrectionDecisionDto {
    private boolean approved;
    private String comment;
    private Long hodId;

    public CorrectionDecisionDto() {}

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Long getHodId() { return hodId; }
    public void setHodId(Long hodId) { this.hodId = hodId; }
}
