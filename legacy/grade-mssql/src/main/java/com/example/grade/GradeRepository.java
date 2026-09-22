package com.example.grade;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 저장 프로시저 호출. 컬럼 순서는 프로시저의 SELECT 순서를 그대로 따른다. */
@Repository
public class GradeRepository {

    private final JdbcTemplate jdbc;

    public GradeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** EXEC dbo.usp_aggregate_grades @class_id → (student_id, name, unit, score, grade) */
    public List<Map<String, String>> aggregateGrades(String classId) {
        return jdbc.query("EXEC dbo.usp_aggregate_grades @class_id = ?", GradeRepository::rowToMap, classId);
    }

    /** EXEC dbo.usp_class_report @class_id → (unit, unit_name, enrolled, submitted, missing, late, excluded, avg_score, max_score, min_score) */
    public List<Map<String, String>> classReport(String classId) {
        return jdbc.query("EXEC dbo.usp_class_report @class_id = ?", GradeRepository::rowToMap, classId);
    }

    private static Map<String, String> rowToMap(ResultSet rs, int rowNum) throws SQLException {
        var meta = rs.getMetaData();
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            Object v = rs.getObject(i);
            row.put(label, format(v));
        }
        return row;
    }

    /** DECIMAL 은 자릿수 그대로(87.5, 78.75), 나머지는 toString. NULL 은 빈 문자열. */
    private static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal d) {
            return d.toPlainString();
        }
        return v.toString().trim();
    }
}
