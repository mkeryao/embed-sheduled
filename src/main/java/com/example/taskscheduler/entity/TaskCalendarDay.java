package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Date;

/**
 * Entity representing a specific day entry within a {@link TaskCalendar}.
 * It defines whether a particular date is considered a working day or a non-working day
 * (e.g., a holiday) for task scheduling purposes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendarDay {
    /** Unique identifier for this calendar day entry. */
    private Integer dayId;

    /** ID of the parent {@link TaskCalendar} to which this day belongs. */
    private int calendarId;

    /** The specific date of this entry (e.g., "2024-12-25"). */
    private Date eventDate;

    /**
     * Flag indicating if this day is a working day.
     * {@code true} if it's a working day, {@code false} if it's a non-working day.
     */
    private boolean isWorkingDay;

    /** Optional description for this day entry (e.g., "Christmas Day", "Company Holiday"). */
    private String description;
}
