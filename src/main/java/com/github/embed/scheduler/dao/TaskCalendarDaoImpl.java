package com.github.embed.scheduler.dao;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.github.embed.scheduler.entity.TaskCalendar;
import com.github.embed.scheduler.entity.TaskCalendarDay;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import javax.annotation.Resource;

/**
 * JDBC implementation of the {@link TaskCalendarDao} interface.
 * Handles database operations for {@link TaskCalendar} and
 * {@link TaskCalendarDay} entities
 * using Spring's {@link JdbcTemplate}.
 * Implements Guava caching for frequently accessed calendar data.
 */
@Repository
public class TaskCalendarDaoImpl implements TaskCalendarDao {

    private static final Logger logger = LoggerFactory.getLogger(TaskCalendarDaoImpl.class);

    @Resource(name = "schedulerJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    // --- Caches ---
    private final Cache<Integer, TaskCalendar> calendarCacheById = CacheBuilder.newBuilder()
            .maximumSize(50) // Max 50 calendar definitions by ID
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private final Cache<String, TaskCalendar> calendarCacheByName = CacheBuilder.newBuilder()
            .maximumSize(50) // Max 50 calendar definitions by Name
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    private final Cache<Integer, List<TaskCalendarDay>> calendarDaysByCalendarIdCache = CacheBuilder.newBuilder()
            .maximumSize(50) // Cache days for 50 calendar groups
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    // Key: "calendarId_yyyy-MM-dd"
    private final Cache<String, TaskCalendarDay> specificCalendarDayCache = CacheBuilder.newBuilder()
            .maximumSize(500) // Max 500 specific day entries
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    // TaskCalendar SQL
    private static final String INSERT_CALENDAR_SQL = "INSERT INTO task_calendar (calendar_name, description) VALUES (?, ?)";
    private static final String UPDATE_CALENDAR_SQL = "UPDATE task_calendar SET calendar_name=?, description=? WHERE calendar_id=?";
    private static final String SELECT_CALENDAR_BY_ID_SQL = "SELECT * FROM task_calendar WHERE calendar_id=?";
    private static final String SELECT_CALENDAR_BY_NAME_SQL = "SELECT * FROM task_calendar WHERE calendar_name=?";
    private static final String SELECT_ALL_CALENDARS_SQL = "SELECT * FROM task_calendar";
    private static final String DELETE_CALENDAR_BY_ID_SQL = "DELETE FROM task_calendar WHERE calendar_id=?";

    // TaskCalendarDay SQL
    private static final String INSERT_CALENDAR_DAY_SQL = "INSERT INTO task_calendar_day (calendar_id, event_date, is_working_day, description) VALUES (?, ?, ?, ?)";
    private static final String UPDATE_CALENDAR_DAY_SQL = "UPDATE task_calendar_day SET calendar_id=?, event_date=?, is_working_day=?, description=? WHERE day_id=?";
    private static final String SELECT_CALENDAR_DAY_BY_ID_SQL = "SELECT * FROM task_calendar_day WHERE day_id=?";
    private static final String SELECT_CALENDAR_DAYS_BY_CALENDAR_ID_SQL = "SELECT * FROM task_calendar_day WHERE calendar_id=?";
    private static final String SELECT_CALENDAR_DAY_BY_CALENDAR_ID_AND_DATE_SQL = "SELECT * FROM task_calendar_day WHERE calendar_id=? AND event_date=?";
    private static final String DELETE_CALENDAR_DAY_BY_ID_SQL = "DELETE FROM task_calendar_day WHERE day_id=?";
    private static final String DELETE_CALENDAR_DAYS_BY_CALENDAR_ID_SQL = "DELETE FROM task_calendar_day WHERE calendar_id=?";

    private final RowMapper<TaskCalendar> calendarRowMapper = (rs, rowNum) -> {
        TaskCalendar calendar = new TaskCalendar();
        calendar.setCalendarId(rs.getInt("calendar_id"));
        calendar.setCalendarName(rs.getString("calendar_name"));
        calendar.setDescription(rs.getString("description"));
        return calendar;
    };

    private final RowMapper<TaskCalendarDay> calendarDayRowMapper = (rs, rowNum) -> {
        TaskCalendarDay day = new TaskCalendarDay();
        day.setDayId(rs.getInt("day_id"));
        day.setCalendarId(rs.getInt("calendar_id"));
        day.setEventDate(rs.getDate("event_date"));
        day.setWorkingDay(rs.getBoolean("is_working_day"));
        day.setDescription(rs.getString("description"));
        return day;
    };

    // --- TaskCalendar methods ---
    @Override
    public TaskCalendar saveCalendar(TaskCalendar calendar) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_CALENDAR_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, calendar.getCalendarName());
            ps.setString(2, calendar.getDescription());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            calendar.setCalendarId(keyHolder.getKey().intValue());
        }
        // Invalidate relevant caches
        if (calendar.getCalendarName() != null) {
            logger.debug("Calendar saved/updated. Invalidating cache for calendar name: {}",
                    calendar.getCalendarName());
            calendarCacheByName.invalidate(calendar.getCalendarName());
        }
        if (calendar.getCalendarId() != null) {
            logger.debug("Invalidating cache for calendar ID: {}", calendar.getCalendarId());
            calendarCacheById.invalidate(calendar.getCalendarId());
            // New calendar group won't have days in day-specific caches yet, but good to
            // clear group list cache
            calendarDaysByCalendarIdCache.invalidate(calendar.getCalendarId());
        }
        return calendar;
    }

    @Override
    public Optional<TaskCalendar> findCalendarById(Integer calendarId) {
        if (calendarId == null)
            return Optional.empty();
        TaskCalendar cachedCalendar = calendarCacheById.getIfPresent(calendarId);
        if (cachedCalendar != null) {
            logger.debug("Cache hit for calendar ID: {}", calendarId);
            return Optional.of(cachedCalendar);
        }
        logger.debug("Cache miss for calendar ID: {}", calendarId);
        try {
            TaskCalendar calendarFromDb = jdbcTemplate.queryForObject(SELECT_CALENDAR_BY_ID_SQL,
                    new Object[] { calendarId }, calendarRowMapper);
            if (calendarFromDb != null) {
                logger.debug("DB hit for calendar ID: {}. Caching result.", calendarId);
                calendarCacheById.put(calendarId, calendarFromDb);
                if (calendarFromDb.getCalendarName() != null) {
                    calendarCacheByName.put(calendarFromDb.getCalendarName(), calendarFromDb);
                }
            }
            return Optional.ofNullable(calendarFromDb);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("Calendar not found in DB for ID: {}", calendarId);
            return Optional.empty();
        }
    }

    @Override
    public Optional<TaskCalendar> findCalendarByName(String calendarName) {
        if (calendarName == null)
            return Optional.empty();
        TaskCalendar cachedCalendar = calendarCacheByName.getIfPresent(calendarName);
        if (cachedCalendar != null) {
            logger.debug("Cache hit for calendar name: {}", calendarName);
            return Optional.of(cachedCalendar);
        }
        logger.debug("Cache miss for calendar name: {}", calendarName);
        try {
            TaskCalendar calendarFromDb = jdbcTemplate.queryForObject(SELECT_CALENDAR_BY_NAME_SQL,
                    new Object[] { calendarName }, calendarRowMapper);
            if (calendarFromDb != null) {
                logger.debug("DB hit for calendar name: {}. Caching result.", calendarName);
                calendarCacheByName.put(calendarName, calendarFromDb);
                if (calendarFromDb.getCalendarId() != null) {
                    calendarCacheById.put(calendarFromDb.getCalendarId(), calendarFromDb);
                }
            }
            return Optional.ofNullable(calendarFromDb);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("Calendar not found in DB for name: {}", calendarName);
            return Optional.empty();
        }
    }

    @Override
    public List<TaskCalendar> findAllCalendars() {
        logger.debug("Executing findAllCalendars (bypasses cache).");
        return jdbcTemplate.query(SELECT_ALL_CALENDARS_SQL, calendarRowMapper);
    }

    @Override
    public int updateCalendar(TaskCalendar calendar) {
        int affectedRows = jdbcTemplate.update(UPDATE_CALENDAR_SQL, calendar.getCalendarName(),
                calendar.getDescription(), calendar.getCalendarId());
        if (affectedRows > 0) {
            logger.debug("Calendar updated. Invalidating caches for calendar ID: {} and name: {}",
                    calendar.getCalendarId(), calendar.getCalendarName());
            if (calendar.getCalendarId() != null)
                calendarCacheById.invalidate(calendar.getCalendarId());
            if (calendar.getCalendarName() != null)
                calendarCacheByName.invalidate(calendar.getCalendarName());
            // Days list for this calendar might have changed if name changed, but less
            // direct impact.
            // For simplicity, not invalidating calendarDaysByCalendarIdCache here unless
            // description changes are critical to it.
        }
        return affectedRows;
    }

    @Override
    public int deleteCalendarById(Integer calendarId) {
        if (calendarId == null)
            return 0;
        Optional<TaskCalendar> calendarOptional = findCalendarById(calendarId); // Uses cache

        // Manually cascade delete associated calendar days
        deleteCalendarDaysByCalendarId(calendarId); // This will handle its own cache invalidations for days

        int affectedRows = jdbcTemplate.update(DELETE_CALENDAR_BY_ID_SQL, calendarId);
        if (affectedRows > 0) {
            logger.debug("Calendar deleted for ID: {}. Invalidating associated caches.", calendarId);
            calendarCacheById.invalidate(calendarId);
            calendarOptional.ifPresent(cal -> {
                if (cal.getCalendarName() != null) {
                    calendarCacheByName.invalidate(cal.getCalendarName());
                }
            });
            // calendarDaysByCalendarIdCache already invalidated by
            // deleteCalendarDaysByCalendarId
        }
        return affectedRows;
    }

    // --- TaskCalendarDay methods ---
    @Override
    public TaskCalendarDay saveCalendarDay(TaskCalendarDay calendarDay) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_CALENDAR_DAY_SQL,
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, calendarDay.getCalendarId());
            ps.setDate(2, calendarDay.getEventDate());
            ps.setBoolean(3, calendarDay.isWorkingDay());
            ps.setString(4, calendarDay.getDescription());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            calendarDay.setDayId(keyHolder.getKey().intValue());
        }
        // Invalidate caches
        logger.debug("Calendar day saved/updated. Invalidating caches for calendar ID: {} and date: {}",
                calendarDay.getCalendarId(), calendarDay.getEventDate());
        calendarDaysByCalendarIdCache.invalidate(calendarDay.getCalendarId());
        if (calendarDay.getEventDate() != null) {
            specificCalendarDayCache
                    .invalidate(
                            calendarDay.getCalendarId() + "_" + calendarDay.getEventDate().toString().substring(0, 10));
        }
        return calendarDay;
    }

    @Override
    public Optional<TaskCalendarDay> findCalendarDayById(Integer dayId) {
        // Individual day by its own ID is less frequently used for caching in this
        // context,
        // but could be added if needed. For now, direct DB access.
        logger.debug("Executing findCalendarDayById for dayId {} (bypasses cache).", dayId);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_CALENDAR_DAY_BY_ID_SQL,
                    new Object[] { dayId }, calendarDayRowMapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskCalendarDay> findCalendarDaysByCalendarId(Integer calendarId) {
        if (calendarId == null)
            return Collections.emptyList();
        List<TaskCalendarDay> cachedDays = calendarDaysByCalendarIdCache.getIfPresent(calendarId);
        if (cachedDays != null) {
            logger.debug("Cache hit for calendar days list for calendar ID: {}", calendarId);
            return cachedDays;
        }
        logger.debug("Cache miss for calendar days list for calendar ID: {}", calendarId);
        List<TaskCalendarDay> daysFromDb = jdbcTemplate.query(SELECT_CALENDAR_DAYS_BY_CALENDAR_ID_SQL,
                new Object[] { calendarId }, calendarDayRowMapper);
        if (daysFromDb != null) { // query typically returns empty list, not null
            logger.debug("DB hit for calendar days list for calendar ID: {}. Caching result.", calendarId);
            calendarDaysByCalendarIdCache.put(calendarId, daysFromDb);
            // Populate specific day cache
            for (TaskCalendarDay day : daysFromDb) {
                if (day.getEventDate() != null) {
                    specificCalendarDayCache.put(day.getCalendarId() + "_" + day.getEventDate().toString()
                            .substring(0, 10), day);
                }
            }
        }
        return daysFromDb;
    }

    @Override
    public Optional<TaskCalendarDay> findCalendarDayByCalendarIdAndDate(Integer calendarId, Date eventDate) {
        if (calendarId == null || eventDate == null) {
            return Optional.empty();
        }
        String cacheKey = calendarId + "_" + eventDate.toString().substring(0, 10);
        TaskCalendarDay cachedDay = specificCalendarDayCache.getIfPresent(cacheKey);
        if (cachedDay != null) {
            logger.debug("Cache hit for specific calendar day: {}", cacheKey);
            return Optional.of(cachedDay);
        }
        logger.debug("Cache miss for specific calendar day: {}", cacheKey);
        try {
            TaskCalendarDay dayFromDb = jdbcTemplate.queryForObject(SELECT_CALENDAR_DAY_BY_CALENDAR_ID_AND_DATE_SQL,
                    new Object[] { calendarId, eventDate }, calendarDayRowMapper);
            if (dayFromDb != null) {
                logger.debug("DB hit for specific calendar day: {}. Caching result.", cacheKey);
                specificCalendarDayCache.put(cacheKey, dayFromDb);
            }
            return Optional.ofNullable(dayFromDb);
        } catch (EmptyResultDataAccessException e) {
            logger.debug("Specific calendar day not found in DB: {}", cacheKey);
            return Optional.empty();
        }
    }

    @Override
    public int updateCalendarDay(TaskCalendarDay calendarDay) {
        int affectedRows = jdbcTemplate.update(UPDATE_CALENDAR_DAY_SQL, calendarDay.getCalendarId(),
                calendarDay.getEventDate(), calendarDay.isWorkingDay(), calendarDay.getDescription(),
                calendarDay.getDayId());
        if (affectedRows > 0) {
            logger.debug("Calendar day updated. Invalidating caches for calendar ID: {} and date: {}",
                    calendarDay.getCalendarId(), calendarDay.getEventDate());
            calendarDaysByCalendarIdCache.invalidate(calendarDay.getCalendarId());
            if (calendarDay.getEventDate() != null) {
                specificCalendarDayCache
                        .invalidate(calendarDay.getCalendarId() + "_"
                                + calendarDay.getEventDate().toString().substring(0, 10));
                // Re-cache the updated day
                specificCalendarDayCache.put(
                        calendarDay.getCalendarId() + "_" + calendarDay.getEventDate().toString().substring(0, 10),
                        calendarDay);
            }
        }
        return affectedRows;
    }

    @Override
    public int deleteCalendarDayById(Integer dayId) {
        if (dayId == null)
            return 0;
        // Fetch day before deleting to get its details for cache invalidation
        Optional<TaskCalendarDay> dayOptional = findCalendarDayById(dayId); // Bypasses cache for now

        int affectedRows = jdbcTemplate.update(DELETE_CALENDAR_DAY_BY_ID_SQL, dayId);

        if (affectedRows > 0 && dayOptional.isPresent()) {
            TaskCalendarDay deletedDay = dayOptional.get();
            logger.debug("Calendar day deleted for ID: {}. Invalidating caches for calendar ID: {} and date: {}",
                    dayId, deletedDay.getCalendarId(), deletedDay.getEventDate());
            calendarDaysByCalendarIdCache.invalidate(deletedDay.getCalendarId());
            if (deletedDay.getEventDate() != null) {
                specificCalendarDayCache
                        .invalidate(deletedDay.getCalendarId() + "_" + deletedDay.getEventDate()
                                .toString().substring(0, 10));
            }
        }
        return affectedRows;
    }

    @Override
    public int deleteCalendarDaysByCalendarId(Integer calendarId) {
        if (calendarId == null)
            return 0;
        // Before deleting, get all days to invalidate specific day cache
        List<TaskCalendarDay> daysToDelete = findCalendarDaysByCalendarId(calendarId); // Uses cache if populated

        int affectedRows = jdbcTemplate.update(DELETE_CALENDAR_DAYS_BY_CALENDAR_ID_SQL, calendarId);

        if (affectedRows > 0) {
            logger.debug("All calendar days deleted for calendar ID: {}. Invalidating caches.", calendarId);
            calendarDaysByCalendarIdCache.invalidate(calendarId);
            if (daysToDelete != null) {
                for (TaskCalendarDay day : daysToDelete) {
                    if (day.getEventDate() != null) {
                        specificCalendarDayCache
                                .invalidate(day.getCalendarId() + "_" + day.getEventDate().toString().substring(0, 10));
                    }
                }
            }
        }
        return affectedRows;
    }
}
