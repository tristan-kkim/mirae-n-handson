package com.example.assignment;

import java.math.BigDecimal;

/** 학급 리포트 — 과제 수 · 제출 수 · 평균 점수 · 리포트 서명(외부 집계 시스템 형식). */
public record ClassReport(
    Integer classId,
    String className,
    int assignmentCount,
    int submissionCount,
    BigDecimal averageScore,
    String signature) {
}
