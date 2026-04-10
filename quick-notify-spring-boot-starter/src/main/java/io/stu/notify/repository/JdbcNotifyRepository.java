package io.stu.notify.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 默认 JdbcTemplate 实现。
 * 用户可实现 {@link NotifyRepository} 接口提供自己的实现。
 */
@Slf4j
public class JdbcNotifyRepository implements NotifyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcNotifyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(NotifyMessageLog msg) {
        String sql = """
            INSERT INTO notify_log (id, type, receiver, data, viewed, created, last_modified, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        jdbcTemplate.update(sql, msg.getId(), msg.getType(), msg.getReceiver(),
                toJson(msg.getData()), msg.isViewed(), msg.getCreated(),
                msg.getCreated(), 0);
    }

    @Override
    public List<NotifyMessageLog> findByReceiver(String userId, int offset, int limit) {
        String sql = """
            SELECT * FROM notify_log
            WHERE receiver = ?
            ORDER BY created DESC
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.query(sql, new NotifyLogMapper(), userId, limit, offset);
    }

    @Override
    public List<NotifyMessageLog> findByReceiverBefore(String userId, long created, int offset, int limit) {
        String sql = """
            SELECT * FROM notify_log
            WHERE receiver = ? AND created < ?
            ORDER BY created DESC
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.query(sql, new NotifyLogMapper(), userId, created, limit, offset);
    }

    @Override
    public long countUnread(String userId) {
        String sql = "SELECT COUNT(*) FROM notify_log WHERE receiver = ? AND viewed = false";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, userId);
        return count != null ? count : 0;
    }

    @Override
    public void markAsRead(String userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = String.format(
                "UPDATE notify_log SET viewed = true WHERE receiver = ? AND id IN (%s)",
                placeholders);
        jdbcTemplate.update(sql, userId, ids.toArray());
    }

    @Override
    public void delete(String userId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        String sql = String.format(
                "DELETE FROM notify_log WHERE receiver = ? AND id IN (%s)",
                placeholders);
        jdbcTemplate.update(sql, userId, ids.toArray());
    }

    private String toJson(Object data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON data: " + data, e);
        }
    }

    private Object fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON data: " + json, e);
        }
    }

    private class NotifyLogMapper implements RowMapper<NotifyMessageLog> {
        @Override
        public NotifyMessageLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            NotifyMessageLog msg = new NotifyMessageLog();
            msg.setId(rs.getString("id"));
            msg.setType(rs.getString("type"));
            msg.setReceiver(rs.getString("receiver"));
            msg.setData(fromJson(rs.getString("data")));
            msg.setViewed(rs.getBoolean("viewed"));
            msg.setCreated(rs.getLong("created"));
            return msg;
        }
    }
}
