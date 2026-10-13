package com.intellicampus.academics.dto;

import com.intellicampus.academics.model.MarkStatus;

public class SaveMarkRequestDto {
    private Long studentId;
    private Long subjectId;
    private String exam;
    private Double marks;
    private Double maxMarks;
    private MarkStatus status = MarkStatus.DRAFT;
    private Long facultyId;

    public SaveMarkRequestDto() {}

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getExam() { return exam; }
    public void setExam(String exam) { this.exam = exam; }

    public Double getMarks() { return marks; }
    public void setMarks(Double marks) { this.marks = marks; }

    public Double getMaxMarks() { return maxMarks; }
    public void setMaxMarks(Double maxMarks) { this.maxMarks = maxMarks; }

    public MarkStatus getStatus() { return status; }
    public void setStatus(MarkStatus status) { this.status = status; }

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }
}
