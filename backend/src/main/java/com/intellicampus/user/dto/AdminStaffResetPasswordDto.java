package com.intellicampus.user.dto;

public class AdminStaffResetPasswordDto {
    private String newPassword;

    public AdminStaffResetPasswordDto() {}

    public AdminStaffResetPasswordDto(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
