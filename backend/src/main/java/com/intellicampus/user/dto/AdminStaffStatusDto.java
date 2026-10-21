package com.intellicampus.user.dto;

import com.intellicampus.user.model.UserStatus;

public class AdminStaffStatusDto {

    private UserStatus status;
    private Boolean active;

    public AdminStaffStatusDto() {}

    public AdminStaffStatusDto(UserStatus status) {
        this.status = status;
    }

    public AdminStaffStatusDto(Boolean active) {
        this.active = active;
        this.status = Boolean.TRUE.equals(active) ? UserStatus.ACTIVE : UserStatus.DISABLED;
    }

    public UserStatus getStatus() {
        if (status != null) {
            return status;
        }
        if (active != null) {
            return active ? UserStatus.ACTIVE : UserStatus.DISABLED;
        }
        return null;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
