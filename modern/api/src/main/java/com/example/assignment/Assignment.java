package com.example.assignment;

import com.example.item.Unit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 과제. 테이블 {@code assignment}. 상태 코드: O=진행 C=마감. */
@Entity
@Table(name = "assignment")
public class Assignment {

    @Id
    private Integer id;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(nullable = false, length = 1)
    private String status;

    protected Assignment() {
        // JPA
    }

    public Assignment(Integer id, String title, Unit unit, LocalDateTime dueAt, String status) {
        this.id = id;
        this.title = title;
        this.unit = unit;
        this.dueAt = dueAt;
        this.status = status;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Unit getUnit() {
        return unit;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public String getStatus() {
        return status;
    }
}
