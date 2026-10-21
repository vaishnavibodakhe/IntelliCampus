package com.intellicampus.auth.dto;

import com.intellicampus.user.model.Role;

public class UserSummaryDto {
    private Long id;
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String rollNo;
    private String department;
    private Integer year;
    private String division;

    public UserSummaryDto() {}

    public UserSummaryDto(Long id, String userCode, String name, String email, Role role, String rollNo, String department, Integer year, String division) {
        this.id = id;
        this.userCode = userCode;
        this.name = name;
        this.email = email;
        this.role = role;
        this.rollNo = rollNo;
        this.department = department;
        this.year = year;
        this.division = division;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserCode() { return userCode; }
    public void setUserCode(String userCode) { this.userCode = userCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }
}
