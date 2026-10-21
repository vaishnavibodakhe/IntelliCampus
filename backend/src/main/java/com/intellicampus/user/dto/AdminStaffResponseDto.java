package com.intellicampus.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.intellicampus.user.model.Role;
import com.intellicampus.user.model.UserStatus;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminStaffResponseDto {

    private Long id;
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String department;
    private Long departmentId;
    private UserStatus status;
    private Boolean mustChangePassword;
    private String temporaryPassword;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AdminStaffResponseDto() {}

    public AdminStaffResponseDto(Long id, String userCode, String name, String email, Role role,
                                 String department, Long departmentId, UserStatus status,
                                 Boolean mustChangePassword, String temporaryPassword,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userCode = userCode;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.departmentId = departmentId;
        this.status = status;
        this.mustChangePassword = mustChangePassword;
        this.temporaryPassword = temporaryPassword;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public Boolean getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public String getTemporaryPassword() { return temporaryPassword; }
    public void setTemporaryPassword(String temporaryPassword) { this.temporaryPassword = temporaryPassword; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
