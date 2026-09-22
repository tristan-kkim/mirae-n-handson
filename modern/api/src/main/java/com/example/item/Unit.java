package com.example.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 단원. 테이블 {@code unit}. 코드는 {@code M5-1} 형식(학년-순번). */
@Entity
@Table(name = "unit")
public class Unit {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer grade;

    protected Unit() {
        // JPA
    }

    public Unit(Integer id, String code, String name, Integer grade) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.grade = grade;
    }

    public Integer getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getGrade() {
        return grade;
    }
}
