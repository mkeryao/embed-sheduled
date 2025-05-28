package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskCalendar;
import com.example.taskscheduler.entity.TaskCalendarDay;
import java.util.List;
import java.util.Optional;
import java.sql.Date;

public interface TaskCalendarDao {
    // TaskCalendar methods
    TaskCalendar saveCalendar(TaskCalendar calendar);
    Optional<TaskCalendar> findCalendarById(Integer calendarId);
    Optional<TaskCalendar> findCalendarByName(String calendarName);
    List<TaskCalendar> findAllCalendars();
    int updateCalendar(TaskCalendar calendar);
    int deleteCalendarById(Integer calendarId);

    // TaskCalendarDay methods
    TaskCalendarDay saveCalendarDay(TaskCalendarDay calendarDay);
    Optional<TaskCalendarDay> findCalendarDayById(Integer dayId);
    List<TaskCalendarDay> findCalendarDaysByCalendarId(Integer calendarId);
    Optional<TaskCalendarDay> findCalendarDayByCalendarIdAndDate(Integer calendarId, Date eventDate);
    int updateCalendarDay(TaskCalendarDay calendarDay);
    int deleteCalendarDayById(Integer dayId);
    int deleteCalendarDaysByCalendarId(Integer calendarId);
}
