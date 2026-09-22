package com.example.item;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 문항 태그. 테이블 {@code tag}. 예: 계산, 문장제, 개념, 오답률높음, 서술형, 도형. */
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    protected Tag() {
        // JPA
    }

    public Tag(Integer id, String name) {
        this.id = id;
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
