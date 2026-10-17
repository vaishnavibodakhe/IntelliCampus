package com.intellicampus.user.dto;

import com.intellicampus.user.model.Role;

public class AuthResponse {

    private String token;
    private String tokenType = "Bearer";
    private Long id;
    private String userCode;
    private String name;
    private String email;
    private Role role;
    private String department;
    private String phone;

    public AuthResponse() {}

    public AuthResponse(String token, Long id, String userCode, String name, String email, Role role, String department, String phone) {
        this.token = token;
        this.tokenType = "Bearer";
        this.id = id;
        this.userCode = userCode;
        this.name = name;
        this.email = email;
        this.role = role;
        this.department = department;
        this.phone = phone;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

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

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
