package com.intellicampus.academic.dto;

public class SubjectDto {
    private Long id;
    private String code;
    private String name;
    private Integer credits;
    private Long departmentId;
    private String departmentName;
    private Long facultyId;
    private String facultyName;

    public SubjectDto() {}

    public SubjectDto(Long id, String code, String name, Integer credits, Long departmentId, String departmentName, Long facultyId, String facultyName) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.facultyId = facultyId;
        this.facultyName = facultyName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Long getFacultyId() { return facultyId; }
    public void setFacultyId(Long facultyId) { this.facultyId = facultyId; }

    public String getFacultyName() { return facultyName; }
    public void setFacultyName(String facultyName) { this.facultyName = facultyName; }
}
