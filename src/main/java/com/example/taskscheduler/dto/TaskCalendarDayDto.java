package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendarDayDto {
    private Integer dayId;
    private Integer calendarId; // To link back, might be redundant if always nested
    private Date eventDate;
    private boolean isWorkingDay;
    private String description;
}
