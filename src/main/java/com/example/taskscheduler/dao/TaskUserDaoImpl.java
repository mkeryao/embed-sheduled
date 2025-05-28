package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskUserDaoImpl implements TaskUserDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String USER_COLUMNS = "user_id, username, password_hash, email, is_admin, create_time, webhook_address";
    private static final String INSERT_SQL = "INSERT INTO task_user (username, password_hash, email, is_admin, webhook_address, create_time) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
    private static final String UPDATE_SQL = "UPDATE task_user SET username=?, password_hash=?, email=?, is_admin=?, webhook_address=? WHERE user_id=?";
    private static final String SELECT_BY_ID_SQL = "SELECT " + USER_COLUMNS + " FROM task_user WHERE user_id=?";
    private static final String SELECT_BY_USERNAME_SQL = "SELECT " + USER_COLUMNS + " FROM task_user WHERE username=?";
    private static final String SELECT_ALL_SQL = "SELECT " + USER_COLUMNS + " FROM task_user";
    private static final String DELETE_BY_ID_SQL = "DELETE FROM task_user WHERE user_id=?";

    private final RowMapper<TaskUser> rowMapper = (rs, rowNum) -> {
        TaskUser user = new TaskUser();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setEmail(rs.getString("email"));
        user.setAdmin(rs.getBoolean("is_admin"));
        user.setCreateTime(rs.getTimestamp("create_time"));
        user.setWebhookAddress(rs.getString("webhook_address"));
        return user;
    };

    @Override
    public TaskUser save(TaskUser user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getEmail());
            ps.setBoolean(4, user.isAdmin());
            ps.setString(5, user.getWebhookAddress());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            user.setUserId(keyHolder.getKey().intValue());
        }
        return user;
    }

    @Override
    public Optional<TaskUser> findById(Integer userId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, new Object[]{userId}, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TaskUser> findByUsername(String username) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_BY_USERNAME_SQL, new Object[]{username}, rowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskUser> findAll() {
        return jdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
    }

    @Override
    public int update(TaskUser user) {
        return jdbcTemplate.update(UPDATE_SQL,
                user.getUsername(), user.getPasswordHash(), user.getEmail(),
                user.isAdmin(), user.getWebhookAddress(), user.getUserId());
    }

    @Override
    public int deleteById(Integer userId) {
        return jdbcTemplate.update(DELETE_BY_ID_SQL, userId);
    }
}
