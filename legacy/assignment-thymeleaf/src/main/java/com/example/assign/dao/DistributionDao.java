package com.example.assign.dao;

import com.example.assign.model.DistributionRow;
import com.example.assign.model.SubmissionRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DistributionDao {

    private final JdbcTemplate jdbc;

    public DistributionDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String BASE_SELECT =
            "SELECT d.id, d.assignment_id, a.title AS assignment_title, a.status AS assignment_status, a.due_at, "
            + " d.class_id, c.name AS class_name, d.distributed_at, d.redistributed, "
            + " (SELECT COUNT(*) FROM submission s WHERE s.distribution_id = d.id AND s.submitted_at IS NOT NULL) AS submitted_cnt "
            + " FROM distribution d "
            + " JOIN assignment a ON a.id = d.assignment_id "
            + " JOIN `class` c ON c.id = d.class_id ";

    public List<DistributionRow> findAllForList() {
        String sql = BASE_SELECT
                + " WHERE a.status <> 'D' "
                + " ORDER BY a.due_at ASC, d.id ASC";
        return jdbc.query(sql, MAPPER);
    }

    public DistributionRow findById(long id) {
        List<DistributionRow> rows = jdbc.query(BASE_SELECT + " WHERE d.id = ?", MAPPER, id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    // 같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)
    public int countByAssignmentAndClass(long assignmentId, long classId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",
                Integer.class, assignmentId, classId);
        return n == null ? 0 : n.intValue();
    }

    public long nextId() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
        return (max == null ? 0L : max.longValue()) + 1L;
    }

    public int insert(long id, long assignmentId, long classId, LocalDateTime distributedAt) {
        return jdbc.update(
                "INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)",
                id, assignmentId, classId, Timestamp.valueOf(distributedAt));
    }

    public int markRedistributed(long id) {
        return jdbc.update("UPDATE distribution SET redistributed = 1 WHERE id = ?", id);
    }

    public List<SubmissionRow> findSubmissions(long distributionId) {
        return jdbc.query(
                "SELECT id, distribution_id, student_id, submitted_at, score FROM submission WHERE distribution_id = ? ORDER BY id ASC",
                SUB_MAPPER, distributionId);
    }

    private static final RowMapper<DistributionRow> MAPPER = new RowMapper<DistributionRow>() {
        @Override
        public DistributionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            DistributionRow d = new DistributionRow();
            d.setId(rs.getLong("id"));
            d.setAssignmentId(rs.getLong("assignment_id"));
            d.setAssignmentTitle(rs.getString("assignment_title"));
            d.setAssignmentStatus(rs.getString("assignment_status"));
            Timestamp due = rs.getTimestamp("due_at");
            d.setDueAt(due == null ? null : due.toLocalDateTime());
            d.setClassId(rs.getLong("class_id"));
            d.setClassName(rs.getString("class_name"));
            Timestamp at = rs.getTimestamp("distributed_at");
            d.setDistributedAt(at == null ? null : at.toLocalDateTime());
            d.setRedistributed(rs.getInt("redistributed"));
            d.setSubmittedCount(rs.getInt("submitted_cnt"));
            return d;
        }
    };

    private static final RowMapper<SubmissionRow> SUB_MAPPER = new RowMapper<SubmissionRow>() {
        @Override
        public SubmissionRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            SubmissionRow s = new SubmissionRow();
            s.setId(rs.getLong("id"));
            s.setDistributionId(rs.getLong("distribution_id"));
            s.setStudentId(rs.getString("student_id"));
            Timestamp at = rs.getTimestamp("submitted_at");
            s.setSubmittedAt(at == null ? null : at.toLocalDateTime());
            s.setScore(rs.getBigDecimal("score"));
            return s;
        }
    };
}
