package com.intellicampus.academic.dto;

public class DivisionDto {
    private Long id;
    private Long departmentId;
    private String departmentName;
    private Integer year;
    private String name;

    public DivisionDto() {}

    public DivisionDto(Long id, Long departmentId, String departmentName, Integer year, String name) {
        this.id = id;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
        this.year = year;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
