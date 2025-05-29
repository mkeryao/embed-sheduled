package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * Data Transfer Object for {@link com.example.taskscheduler.entity.TaskCalendar}.
 * Represents a calendar, which is a named group of special days (e.g., holidays).
 * This DTO can include the list of associated {@link TaskCalendarDayDto} entries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendarDto {
    /**
     * The unique identifier for the calendar.
     */
    private Integer calendarId;

    /**
     * The unique name of the calendar (e.g., "NATIONAL_HOLIDAYS", "COMPANY_EVENTS").
     * This name is used in {@link TaskConfigDto#taskCalendarGroup} to link tasks to this calendar
     * for exclusion purposes.
     */
    private String calendarName;

    /**
     * An optional description for the calendar.
     */
    private String description;

    /**
     * A list of specific day entries ({@link TaskCalendarDayDto}) associated with this calendar.
     * This is typically populated when fetching a single calendar or all calendars with details.
     */
    private List<TaskCalendarDayDto> days;
}
