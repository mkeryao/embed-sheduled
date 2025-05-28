package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendar {
    private Integer calendarId;
    private String calendarName;
    private String description;
}
