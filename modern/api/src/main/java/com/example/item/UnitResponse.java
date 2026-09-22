package com.example.item;

/** {@code GET /api/units} 응답 항목. */
public record UnitResponse(Integer id, String code, String name, Integer grade) {

    static UnitResponse from(Unit unit) {
        return new UnitResponse(unit.getId(), unit.getCode(), unit.getName(), unit.getGrade());
    }
}
