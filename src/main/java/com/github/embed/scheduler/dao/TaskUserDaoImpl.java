package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.TaskUser;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
// ResultSet and SQLException are used by RowMapper lambda, but not directly in the main class body now
// import java.sql.ResultSet;
// import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * JDBC implementation of the {@link TaskUserDao} interface.
 * Handles database operations for {@link TaskUser} entities
 * using Spring's {@link JdbcTemplate}.
 * Implements Guava caching for frequently accessed user data.
 */
@Repository
public class TaskUserDaoImpl implements TaskUserDao {

    private static final Logger logger = LoggerFactory.getLogger(TaskUserDaoImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Cache for {@link TaskUser} objects by their Integer ID. Configured for max 100 users, 1-hour expiry. */
    private final Cache<Integer, TaskUser> userCacheById = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    /** Cache for {@link TaskUser} objects by their String username. Configured for max 100 users, 1-hour expiry. */
    private final Cache<String, TaskUser> userCacheByUsername = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private static final String USER_COLUMNS = "user_id, username, password_hash, email, is_admin, create_time, webhook_address, notification_preferences_json";
    private static final String INSERT_SQL = "INSERT INTO task_user (username, password_hash, email, is_admin, webhook_address, notification_preferences_json, create_time) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
    private static final String UPDATE_SQL = "UPDATE task_user SET username=?, password_hash=?, email=?, is_admin=?, webhook_address=?, notification_preferences_json=? WHERE user_id=?";
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
        user.setNotificationPreferencesJson(rs.getString("notification_preferences_json"));
        return user;
    };

    @Override
    public TaskUser save(TaskUser user) {
        // If user has an ID, it might be an update, which could change the username.
        // Invalidate by ID first to handle potential username changes.
        if (user.getUserId() != null) {
            TaskUser oldUser = userCacheById.getIfPresent(user.getUserId());
            if (oldUser != null && !oldUser.getUsername().equals(user.getUsername())) {
                logger.debug("Username changed for user ID: {}. Invalidating old username cache entry: {}", user.getUserId(), oldUser.getUsername());
                userCacheByUsername.invalidate(oldUser.getUsername());
            }
            userCacheById.invalidate(user.getUserId());
        }
        // Also invalidate by current username in case other details changed for an existing user by that name
        if (user.getUsername() != null) {
             userCacheByUsername.invalidate(user.getUsername());
        }


        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getEmail());
            ps.setBoolean(4, user.isAdmin());
            ps.setString(5, user.getWebhookAddress());
            ps.setString(6, user.getNotificationPreferencesJson());
            return ps;
        }, keyHolder);

        if (keyHolder.getKey() != null) {
            user.setUserId(keyHolder.getKey().intValue());
        }
        // After save (insert or update-like behavior by application logic), cache the potentially updated user.
        if (user.getUserId() != null && user.getUsername() != null) {
            logger.debug("Caching saved user ID: {}, username: {}", user.getUserId(), user.getUsername());
            userCacheById.put(user.getUserId(), user);
            userCacheByUsername.put(user.getUsername(), user);
        }
        return user;
    }

    @Override
    public Optional<TaskUser> findById(Integer userId) {
        if (userId == null) return Optional.empty();
        TaskUser cachedUser = userCacheById.getIfPresent(userId);
        if (cachedUser != null) {
            logger.debug("Cache hit for user ID: {}", userId);
            return Optional.of(cachedUser);
        }
        logger.debug("Cache miss for user ID: {}", userId);
        try {
            TaskUser userFromDb = jdbcTemplate.queryForObject(SELECT_BY_ID_SQL, new Object[]{userId}, rowMapper);
            if (userFromDb != null) {
                logger.debug("DB hit for user ID: {}. Caching result.", userId);
                userCacheById.put(userId, userFromDb);
                if (userFromDb.getUsername() != null) { // Ensure username is not null before caching by username
                   userCacheByUsername.put(userFromDb.getUsername(), userFromDb);
                }
            }
            return Optional.ofNullable(userFromDb);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("User not found in DB for ID: {}", userId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<TaskUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        TaskUser cachedUser = userCacheByUsername.getIfPresent(username);
        if (cachedUser != null) {
            logger.debug("Cache hit for username: {}", username);
            return Optional.of(cachedUser);
        }
        logger.debug("Cache miss for username: {}", username);
        try {
            TaskUser userFromDb = jdbcTemplate.queryForObject(SELECT_BY_USERNAME_SQL, new Object[]{username}, rowMapper);
            if (userFromDb != null) {
                logger.debug("DB hit for username: {}. Caching result.", username);
                userCacheByUsername.put(username, userFromDb);
                if (userFromDb.getUserId() != null) { // Ensure ID is not null before caching by ID
                    userCacheById.put(userFromDb.getUserId(), userFromDb);
                }
            }
            return Optional.ofNullable(userFromDb);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("User not found in DB for username: {}", username);
            return Optional.empty();
        }
    }

    @Override
    public List<TaskUser> findAll() {
        // Caching for findAll is typically not done at this level unless data changes very infrequently.
        // For now, it bypasses the cache.
        logger.debug("Executing findAll (bypasses cache).");
        return jdbcTemplate.query(SELECT_ALL_SQL, rowMapper);
    }

    @Override
    public int update(TaskUser user) {
        int affectedRows = jdbcTemplate.update(UPDATE_SQL,
                user.getUsername(), user.getPasswordHash(), user.getEmail(),
                user.isAdmin(), user.getWebhookAddress(), user.getNotificationPreferencesJson(),
                user.getUserId());

        if (affectedRows > 0 && user.getUserId() != null && user.getUsername() != null) {
            // Invalidate both caches as username or other details might have changed.
            // Then re-cache the updated user object.
            logger.debug("User updated. Invalidating and re-caching for user ID: {}, username: {}", user.getUserId(), user.getUsername());

            // Fetch the user that was actually updated from DB to get potentially old username if it changed.
            // However, simpler to invalidate current known identifiers and re-cache the new state.
            TaskUser oldUserFromIdCache = userCacheById.getIfPresent(user.getUserId());
            if(oldUserFromIdCache != null && !oldUserFromIdCache.getUsername().equals(user.getUsername())) {
                 logger.debug("Username changed during update for user ID: {}. Old username: '{}', New: '{}'. Invalidating old username cache.",
                              user.getUserId(), oldUserFromIdCache.getUsername(), user.getUsername());
                 userCacheByUsername.invalidate(oldUserFromIdCache.getUsername());
            }

            userCacheById.invalidate(user.getUserId());
            userCacheByUsername.invalidate(user.getUsername());

            // Re-cache the new state. It's important that `user` object is the state after update.
            // If `user` object passed in might not be the full final state, a findById might be better before caching.
            // Assuming 'user' is the new state:
            userCacheById.put(user.getUserId(), user);
            userCacheByUsername.put(user.getUsername(), user);
        }
        return affectedRows;
    }

    @Override
    public int deleteById(Integer userId) {
        if (userId == null) return 0;
        // Fetch user before deleting to get username for cache invalidation
        Optional<TaskUser> userOptional = findById(userId); // This will use cache if available

        int affectedRows = jdbcTemplate.update(DELETE_BY_ID_SQL, userId);

        if (affectedRows > 0) {
            logger.debug("User deleted for ID: {}. Invalidating caches.", userId);
            userCacheById.invalidate(userId);
            userOptional.ifPresent(user -> {
                if (user.getUsername() != null) {
                    userCacheByUsername.invalidate(user.getUsername());
                }
            });
        }
        return affectedRows;
    }
}
