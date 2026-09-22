package com.example.assign.dao;

import com.example.assign.model.AssignmentRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentDao {

    private final JdbcTemplate jdbc;

    public AssignmentDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SELECT =
            "SELECT a.id, a.title, a.unit_id, u.code AS unit_code, u.name AS unit_name, a.due_at, a.status, "
            + " (SELECT COUNT(DISTINCT d.class_id) FROM distribution d WHERE d.assignment_id = a.id) AS class_cnt "
            + " FROM assignment a LEFT JOIN unit u ON u.id = a.unit_id ";

    // 목록: 삭제('D') 제외, 마감 빠른 순
    public List<AssignmentRow> findAllForList() {
        String sql = BASE_SELECT
                + " WHERE a.status <> 'D' "
                + " ORDER BY a.due_at ASC, a.id ASC";
        return jdbc.query(sql, MAPPER);
    }

    public List<AssignmentRow> findAllForSelect() {
        String sql = BASE_SELECT
                + " WHERE a.status IN ('O', 'X', 'C') "
                + " ORDER BY a.due_at ASC, a.id ASC";
        return jdbc.query(sql, MAPPER);
    }

    public AssignmentRow findById(long id) {
        String sql = BASE_SELECT + " WHERE a.id = ?";
        List<AssignmentRow> rows = jdbc.query(sql, MAPPER, id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private static final RowMapper<AssignmentRow> MAPPER = new RowMapper<AssignmentRow>() {
        @Override
        public AssignmentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            AssignmentRow r = new AssignmentRow();
            r.setId(rs.getLong("id"));
            r.setTitle(rs.getString("title"));
            r.setUnitId(rs.getInt("unit_id"));
            r.setUnitCode(rs.getString("unit_code"));
            r.setUnitName(rs.getString("unit_name"));
            Timestamp due = rs.getTimestamp("due_at");
            r.setDueAt(due == null ? null : due.toLocalDateTime());
            r.setStatus(rs.getString("status"));
            r.setClassCount(rs.getInt("class_cnt"));
            return r;
        }
    };
}
