package com.example.assignment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 과제 배포 이력. 테이블 {@code distribution}. 한 과제를 한 학급에 배포한 기록 한 건. */
@Entity
@Table(name = "distribution")
public class Distribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private ClassRoom classRoom;

    @Column(name = "distributed_at", nullable = false)
    private LocalDateTime distributedAt;

    /** 재배포 여부(0/1). */
    @Column(nullable = false)
    private boolean redistributed;

    protected Distribution() {
        // JPA
    }

    public Distribution(Assignment assignment, ClassRoom classRoom, LocalDateTime distributedAt) {
        this.assignment = assignment;
        this.classRoom = classRoom;
        this.distributedAt = distributedAt;
        this.redistributed = false;
    }

    /** 재배포 — 배포 시각을 갱신하고 재배포 표시를 남긴다. */
    public void markRedistributed(LocalDateTime at) {
        this.distributedAt = at;
        this.redistributed = true;
    }

    public Integer getId() {
        return id;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public ClassRoom getClassRoom() {
        return classRoom;
    }

    public LocalDateTime getDistributedAt() {
        return distributedAt;
    }

    public boolean isRedistributed() {
        return redistributed;
    }
}
