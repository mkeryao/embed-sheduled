package com.example.taskscheduler.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.sql.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCalendarDay {
    private Integer dayId;
    private int calendarId;
    private Date eventDate;
    private boolean isWorkingDay;
    private String description;
}
