package com.example.assign.dao;

import com.example.assign.model.ClassRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClassDao {

    private final JdbcTemplate jdbc;

    public ClassDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ClassRow> findAll() {
        return jdbc.query("SELECT id, name, teacher_id FROM `class` ORDER BY id ASC", MAPPER);
    }

    public ClassRow findById(long id) {
        List<ClassRow> rows = jdbc.query("SELECT id, name, teacher_id FROM `class` WHERE id = ?", MAPPER, id);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0);
    }

    private static final RowMapper<ClassRow> MAPPER = new RowMapper<ClassRow>() {
        @Override
        public ClassRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            ClassRow c = new ClassRow();
            c.setId(rs.getLong("id"));
            c.setName(rs.getString("name"));
            c.setTeacherId(rs.getString("teacher_id"));
            return c;
        }
    };
}
