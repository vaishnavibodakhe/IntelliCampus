package com.intellicampus.attendance.dto;

public class DynamicQrTokenDto {
    private long slot;
    private String token;
    private int secondsLeft;

    public DynamicQrTokenDto() {}

    public DynamicQrTokenDto(long slot, String token, int secondsLeft) {
        this.slot = slot;
        this.token = token;
        this.secondsLeft = secondsLeft;
    }

    public long getSlot() { return slot; }
    public void setSlot(long slot) { this.slot = slot; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getSecondsLeft() { return secondsLeft; }
    public void setSecondsLeft(int secondsLeft) { this.secondsLeft = secondsLeft; }
}
