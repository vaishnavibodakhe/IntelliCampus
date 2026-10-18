package com.intellicampus.auth.dto;

public class LoginRequestDto {

    private String userCode;
    private String loginCode;
    private String password;
    private String portal;
    private String expectedRole;

    public LoginRequestDto() {}

    public LoginRequestDto(String userCode, String password) {
        this.userCode = userCode;
        this.loginCode = userCode;
        this.password = password;
    }

    public LoginRequestDto(String userCode, String password, String expectedRole) {
        this.userCode = userCode;
        this.loginCode = userCode;
        this.password = password;
        this.expectedRole = expectedRole;
        this.portal = expectedRole;
    }

    public String getUserCode() {
        if (userCode != null && !userCode.isBlank()) return userCode;
        return loginCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
        if (this.loginCode == null) this.loginCode = userCode;
    }

    public String getLoginCode() {
        if (loginCode != null && !loginCode.isBlank()) return loginCode;
        return userCode;
    }

    public void setLoginCode(String loginCode) {
        this.loginCode = loginCode;
        if (this.userCode == null) this.userCode = loginCode;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPortal() {
        return portal;
    }

    public void setPortal(String portal) {
        this.portal = portal;
        if (this.expectedRole == null) {
            this.expectedRole = portal;
        }
    }

    public String getExpectedRole() {
        if (expectedRole != null && !expectedRole.isBlank()) {
            return expectedRole;
        }
        return portal;
    }

    public void setExpectedRole(String expectedRole) {
        this.expectedRole = expectedRole;
        if (this.portal == null) {
            this.portal = expectedRole;
        }
    }
}
