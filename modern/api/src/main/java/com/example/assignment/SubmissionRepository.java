package com.example.assignment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, Integer> {

    List<Submission> findByDistributionIdOrderByStudentIdAsc(Integer distributionId);
}
