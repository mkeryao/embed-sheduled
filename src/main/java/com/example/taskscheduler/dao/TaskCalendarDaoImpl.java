package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskCalendar;
import com.example.taskscheduler.entity.TaskCalendarDay;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of the {@link TaskCalendarDao} interface.
 * Handles database operations for {@link TaskCalendar} and {@link TaskCalendarDay} entities
 * using Spring's {@link JdbcTemplate}.
 */
@Repository
public class TaskCalendarDaoImpl implements TaskCalendarDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    // TaskCalendar methods
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
        return calendar;
    }

    @Override
    public Optional<TaskCalendar> findCalendarById(Integer calendarId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_CALENDAR_BY_ID_SQL, new Object[]{calendarId}, calendarRowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<TaskCalendar> findCalendarByName(String calendarName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_CALENDAR_BY_NAME_SQL, new Object[]{calendarName}, calendarRowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskCalendar> findAllCalendars() {
        return jdbcTemplate.query(SELECT_ALL_CALENDARS_SQL, calendarRowMapper);
    }

    @Override
    public int updateCalendar(TaskCalendar calendar) {
        return jdbcTemplate.update(UPDATE_CALENDAR_SQL, calendar.getCalendarName(), calendar.getDescription(), calendar.getCalendarId());
    }

    @Override
    public int deleteCalendarById(Integer calendarId) {
        // Manually cascade delete associated calendar days before deleting the calendar itself.
        // This is important if the database schema does not define ON DELETE CASCADE for the foreign key.
        jdbcTemplate.update(DELETE_CALENDAR_DAYS_BY_CALENDAR_ID_SQL, calendarId);
        return jdbcTemplate.update(DELETE_CALENDAR_BY_ID_SQL, calendarId);
    }

    // TaskCalendarDay methods
    @Override
    public TaskCalendarDay saveCalendarDay(TaskCalendarDay calendarDay) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_CALENDAR_DAY_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, calendarDay.getCalendarId());
            ps.setDate(2, calendarDay.getEventDate());
            ps.setBoolean(3, calendarDay.isWorkingDay());
            ps.setString(4, calendarDay.getDescription());
            return ps;
        }, keyHolder);
        if (keyHolder.getKey() != null) {
            calendarDay.setDayId(keyHolder.getKey().intValue());
        }
        return calendarDay;
    }

    @Override
    public Optional<TaskCalendarDay> findCalendarDayById(Integer dayId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_CALENDAR_DAY_BY_ID_SQL, new Object[]{dayId}, calendarDayRowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskCalendarDay> findCalendarDaysByCalendarId(Integer calendarId) {
        return jdbcTemplate.query(SELECT_CALENDAR_DAYS_BY_CALENDAR_ID_SQL, new Object[]{calendarId}, calendarDayRowMapper);
    }
    
    @Override
    public Optional<TaskCalendarDay> findCalendarDayByCalendarIdAndDate(Integer calendarId, Date eventDate) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_CALENDAR_DAY_BY_CALENDAR_ID_AND_DATE_SQL, new Object[]{calendarId, eventDate}, calendarDayRowMapper));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public int updateCalendarDay(TaskCalendarDay calendarDay) {
        return jdbcTemplate.update(UPDATE_CALENDAR_DAY_SQL, calendarDay.getCalendarId(), calendarDay.getEventDate(), calendarDay.isWorkingDay(), calendarDay.getDescription(), calendarDay.getDayId());
    }

    @Override
    public int deleteCalendarDayById(Integer dayId) {
        return jdbcTemplate.update(DELETE_CALENDAR_DAY_BY_ID_SQL, dayId);
    }

    @Override
    public int deleteCalendarDaysByCalendarId(Integer calendarId) {
        return jdbcTemplate.update(DELETE_CALENDAR_DAYS_BY_CALENDAR_ID_SQL, calendarId);
    }
}
