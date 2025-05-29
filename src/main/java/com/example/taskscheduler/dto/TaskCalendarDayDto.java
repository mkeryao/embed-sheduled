package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.sql.Date;

/**
 * Data Transfer Object for {@link com.example.taskscheduler.entity.TaskCalendarDay}.
 * Represents a specific day within a task calendar, indicating whether it's a working day or not.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendarDayDto {
    /**
     * The unique identifier for this calendar day entry.
     */
    private Integer dayId;

    /**
     * The ID of the parent {@link TaskCalendarDto} to which this day entry belongs.
     * This might be redundant if the DTO is always nested within a {@code TaskCalendarDto}.
     */
    private Integer calendarId;

    /**
     * The specific date of this calendar day entry (e.g., "2024-12-25").
     */
    private Date eventDate;

    /**
     * Flag indicating if this day is a working day.
     * {@code true} if it's a working day, {@code false} if it's a non-working day (e.g., a holiday).
     */
    private boolean isWorkingDay;

    /**
     * An optional description for this calendar day entry (e.g., "Christmas Day").
     */
    private String description;
}
