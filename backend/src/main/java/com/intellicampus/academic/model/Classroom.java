package com.intellicampus.academic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms")
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusM = 50;

    @Column
    private Integer capacity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Classroom() {}

    public Classroom(String name, String code, Double latitude, Double longitude, Integer radiusM) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM != null ? radiusM : 50;
        this.createdAt = LocalDateTime.now();
    }

    public Classroom(String name, Double latitude, Double longitude, Integer radiusM, Integer capacity, Department department) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusM = radiusM != null ? radiusM : 50;
        this.capacity = capacity;
        this.department = department;
        this.createdAt = LocalDateTime.now();
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

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

