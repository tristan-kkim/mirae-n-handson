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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 학생 제출. 테이블 {@code submission}. 학생 ID는 {@code STU-1001} 형식. */
@Entity
@Table(name = "submission")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distribution_id", nullable = false)
    private Distribution distribution;

    @Column(name = "student_id", nullable = false, length = 20)
    private String studentId;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** 점수 0.0~100.0, 미채점이면 null. */
    @Column(precision = 5, scale = 1)
    private BigDecimal score;

    protected Submission() {
        // JPA
    }

    public Submission(Distribution distribution, String studentId, LocalDateTime submittedAt, BigDecimal score) {
        this.distribution = distribution;
        this.studentId = studentId;
        this.submittedAt = submittedAt;
        this.score = score;
    }

    public Integer getId() {
        return id;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public String getStudentId() {
        return studentId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public BigDecimal getScore() {
        return score;
    }
}
