package com.example.item;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Integer> {

    Optional<Unit> findByCode(String code);

    List<Unit> findAllByOrderByGradeAscCodeAsc();
}
