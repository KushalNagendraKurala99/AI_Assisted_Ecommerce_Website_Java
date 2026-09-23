package com.ecommerce.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class DbService {

    private final JdbcTemplate jdbc;

    public DbService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // SELECT → list of maps
    public List<Map<String, Object>> queryList(String sql, Object... params) {
        return jdbc.queryForList(sql, params);
    }

    // INSERT / UPDATE / DELETE
    public void update(String sql, Object... params) {
        jdbc.update(sql, params);
    }

    // INSERT → returns generated primary key
    public long insert(String sql, Object... params) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : -1L;
    }

    // Run multiple statements in ONE transaction — all or nothing
    // Each Object[] = { "sql string", new Object[]{param1, param2, ...} }
    @Transactional
    public void transaction(List<Object[]> statements) {
        for (Object[] stmt : statements) {
            String sql = (String) stmt[0];
            Object[] params = (stmt.length > 1 && stmt[1] != null)
                    ? (Object[]) stmt[1]
                    : new Object[0];
            jdbc.update(sql, params);
        }
    }
}
