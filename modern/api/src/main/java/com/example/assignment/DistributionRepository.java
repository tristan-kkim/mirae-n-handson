package com.example.assignment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionRepository extends JpaRepository<Distribution, Integer> {

    @EntityGraph(attributePaths = {"assignment", "classRoom"})
    Optional<Distribution> findWithDetailsById(Integer id);

    @EntityGraph(attributePaths = {"assignment", "classRoom"})
    List<Distribution> findByClassRoomIdOrderByDistributedAtAsc(Integer classRoomId);
}
