package com.github.embed.scheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Entity representing a task calendar.
 * A calendar is a named collection of {@link TaskCalendarDay} entries,
 * which define specific dates as working or non-working days.
 * Tasks can be configured to respect a calendar (e.g., not run on holidays defined in it).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendar {
    /** Unique identifier for the calendar. */
    private Integer calendarId;

    /**
     * Unique name for the calendar (e.g., "NATIONAL_HOLIDAYS", "COMPANY_BLACKOUT_PERIODS").
     * This name is referenced in {@link TaskConfig#taskCalendarGroup}.
     */
    private String calendarName;

    /** Optional description for the calendar's purpose or scope. */
    private String description;
}
