package com.intellicampus.academic.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "divisions")
public class Division {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Division() {}

    public Division(Department department, Integer year, String name) {
        this.department = department;
        this.year = year;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
