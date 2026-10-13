package com.intellicampus.academic.dto;

public class RoomDto {
    private Long id;
    private String name;
    private Double latitude;
    private Double longitude;
    private Integer radiusM;
    private Integer capacity;
    private Long departmentId;
    private String departmentName;

    public RoomDto() {}

    public RoomDto(Long id, String name, Double latitude, Double longitude, Integer radiusM, Integer capacity, Long departmentId, String departmentName) {
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM;
        this.capacity = capacity;
        this.departmentId = departmentId;
        this.departmentName = departmentName;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Integer getRadiusM() { return radiusM; }
    public void setRadiusM(Integer radiusM) { this.radiusM = radiusM; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
}
