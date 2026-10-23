package com.intellicampus.auth.dto;

public class PrincipalSetupStatusDto {
    private boolean setupRequired;

    public PrincipalSetupStatusDto() {}

    public PrincipalSetupStatusDto(boolean setupRequired) {
        this.setupRequired = setupRequired;
    }

    public boolean isSetupRequired() {
        return setupRequired;
    }

    public void setSetupRequired(boolean setupRequired) {
        this.setupRequired = setupRequired;
    }
}
