package com.example.taskscheduler.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendarDto {
    private Integer calendarId;
    private String calendarName;
    private String description;
    private List<TaskCalendarDayDto> days; // To include days when fetching a calendar
}
