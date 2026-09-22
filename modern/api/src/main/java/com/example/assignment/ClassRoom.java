package com.example.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 학급. 테이블 이름 {@code class} 는 자바 예약어라 엔티티 이름을 {@code ClassRoom} 으로 둔다. */
@Entity
@Table(name = "class")
public class ClassRoom {

    @Id
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "teacher_id", nullable = false, length = 20)
    private String teacherId;

    protected ClassRoom() {
        // JPA
    }

    public ClassRoom(Integer id, String name, String teacherId) {
        this.id = id;
        this.name = name;
        this.teacherId = teacherId;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeacherId() {
        return teacherId;
    }
}
