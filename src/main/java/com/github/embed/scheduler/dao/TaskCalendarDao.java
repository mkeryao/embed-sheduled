package com.github.embed.scheduler.dao;

import com.github.embed.scheduler.entity.TaskCalendar;
import com.github.embed.scheduler.entity.TaskCalendarDay;
import java.util.List;
import java.util.Optional;
import java.sql.Date;

/**
 * Data Access Object interface for {@link TaskCalendar} and {@link TaskCalendarDay} entities.
 * Defines methods for managing calendars (groups of special dates) and the specific days within them.
 */
public interface TaskCalendarDao {
    // --- TaskCalendar methods ---

    /**
     * Saves a new task calendar or updates an existing one.
     * @param calendar The calendar entity to save.
     * @return The saved calendar entity, potentially with a generated ID if new.
     */
    TaskCalendar saveCalendar(TaskCalendar calendar);

    /**
     * Finds a calendar by its ID.
     * @param calendarId The ID of the calendar.
     * @return An {@link Optional} containing the calendar if found, or empty otherwise.
     */
    Optional<TaskCalendar> findCalendarById(Integer calendarId);

    /**
     * Finds a calendar by its unique name.
     * @param calendarName The name of the calendar.
     * @return An {@link Optional} containing the calendar if found, or empty otherwise.
     */
    Optional<TaskCalendar> findCalendarByName(String calendarName);

    /**
     * Retrieves all calendars.
     * @return A list of all calendars.
     */
    List<TaskCalendar> findAllCalendars();

    /**
     * Updates an existing calendar.
     * @param calendar The calendar entity with updated values.
     * @return The number of rows affected.
     */
    int updateCalendar(TaskCalendar calendar);

    /**
     * Deletes a calendar by its ID. This will also delete all associated {@link TaskCalendarDay} entries
     * due to database foreign key constraints or explicit DAO implementation logic.
     * @param calendarId The ID of the calendar to delete.
     * @return The number of rows affected in the `task_calendar` table.
     */
    int deleteCalendarById(Integer calendarId);

    // --- TaskCalendarDay methods ---

    /**
     * Saves a new calendar day entry or updates an existing one.
     * @param calendarDay The calendar day entity to save.
     * @return The saved calendar day entity, potentially with a generated ID if new.
     */
    TaskCalendarDay saveCalendarDay(TaskCalendarDay calendarDay);

    /**
     * Finds a specific calendar day entry by its ID.
     * @param dayId The ID of the calendar day entry.
     * @return An {@link Optional} containing the calendar day if found, or empty otherwise.
     */
    Optional<TaskCalendarDay> findCalendarDayById(Integer dayId);

    /**
     * Finds all day entries associated with a specific calendar ID.
     * @param calendarId The ID of the parent calendar.
     * @return A list of calendar day entries for the specified calendar.
     */
    List<TaskCalendarDay> findCalendarDaysByCalendarId(Integer calendarId);

    /**
     * Finds a specific calendar day entry by its parent calendar ID and the event date.
     * @param calendarId The ID of the parent calendar.
     * @param eventDate The specific date of the event.
     * @return An {@link Optional} containing the calendar day if found, or empty otherwise.
     */
    Optional<TaskCalendarDay> findCalendarDayByCalendarIdAndDate(Integer calendarId, Date eventDate);

    /**
     * Updates an existing calendar day entry.
     * @param calendarDay The calendar day entity with updated values.
     * @return The number of rows affected.
     */
    int updateCalendarDay(TaskCalendarDay calendarDay);

    /**
     * Deletes a calendar day entry by its ID.
     * @param dayId The ID of the calendar day entry to delete.
     * @return The number of rows affected.
     */
    int deleteCalendarDayById(Integer dayId);

    /**
     * Deletes all day entries associated with a specific calendar ID.
     * Useful when deleting a parent {@link TaskCalendar}.
     * @param calendarId The ID of the parent calendar whose day entries are to be deleted.
     * @return The number of rows affected.
     */
    int deleteCalendarDaysByCalendarId(Integer calendarId);
}
