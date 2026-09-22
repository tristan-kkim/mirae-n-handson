package com.example.assignment;

import java.time.LocalDateTime;

/** 배포 이력 한 건. */
public record DistributionResponse(
    Integer id,
    Integer assignmentId,
    String assignmentTitle,
    Integer classId,
    LocalDateTime distributedAt,
    boolean redistributed) {

    static DistributionResponse from(Distribution d) {
        return new DistributionResponse(
            d.getId(),
            d.getAssignment().getId(),
            d.getAssignment().getTitle(),
            d.getClassRoom().getId(),
            d.getDistributedAt(),
            d.isRedistributed());
    }
}
