package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskCalendarDao;
import com.example.taskscheduler.dto.TaskCalendarDto;
import com.example.taskscheduler.dto.TaskCalendarDayDto;
import com.example.taskscheduler.entity.TaskCalendar;
import com.example.taskscheduler.entity.TaskCalendarDay;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/embed-api/calendars")
public class TaskCalendarController {

    @Autowired
    private TaskCalendarDao taskCalendarDao;

    // --- DTO Mappers ---
    private TaskCalendarDayDto convertDayToDto(TaskCalendarDay day) {
        if (day == null) return null;
        TaskCalendarDayDto dto = new TaskCalendarDayDto();
        BeanUtils.copyProperties(day, dto);
        return dto;
    }

    private TaskCalendarDay convertDayDtoToEntity(TaskCalendarDayDto dto) {
        if (dto == null) return null;
        TaskCalendarDay day = new TaskCalendarDay();
        BeanUtils.copyProperties(dto, day);
        return day;
    }

    private TaskCalendarDto convertCalendarToDto(TaskCalendar calendar, boolean fetchDays) {
        if (calendar == null) return null;
        TaskCalendarDto dto = new TaskCalendarDto();
        BeanUtils.copyProperties(calendar, dto);
        if (fetchDays && calendar.getCalendarId() != null) {
            List<TaskCalendarDay> days = taskCalendarDao.findCalendarDaysByCalendarId(calendar.getCalendarId());
            dto.setDays(days.stream().map(this::convertDayToDto).collect(Collectors.toList()));
        }
        return dto;
    }

    private TaskCalendar convertCalendarDtoToEntity(TaskCalendarDto dto) {
        if (dto == null) return null;
        TaskCalendar calendar = new TaskCalendar();
        BeanUtils.copyProperties(dto, calendar);
        return calendar;
    }

    // --- Calendar Endpoints ---

    @PostMapping
    public ResponseEntity<TaskCalendarDto> createCalendar(@RequestBody TaskCalendarDto calendarDto) {
        if (calendarDto == null || calendarDto.getCalendarName() == null || calendarDto.getCalendarName().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (taskCalendarDao.findCalendarByName(calendarDto.getCalendarName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null); // Name conflict
        }

        TaskCalendar calendar = convertCalendarDtoToEntity(calendarDto);
        calendar.setCalendarId(null); // Ensure new
        TaskCalendar savedCalendar = taskCalendarDao.saveCalendar(calendar);

        if (calendarDto.getDays() != null && !calendarDto.getDays().isEmpty()) {
            Integer calendarId = savedCalendar.getCalendarId();
            calendarDto.getDays().forEach(dayDto -> {
                TaskCalendarDay day = convertDayDtoToEntity(dayDto);
                day.setCalendarId(calendarId);
                taskCalendarDao.saveCalendarDay(day);
            });
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(convertCalendarToDto(savedCalendar, true));
    }

    @GetMapping
    public ResponseEntity<List<TaskCalendarDto>> getAllCalendars() {
        List<TaskCalendar> calendars = taskCalendarDao.findAllCalendars();
        List<TaskCalendarDto> dtos = calendars.stream()
                                              .map(cal -> convertCalendarToDto(cal, true)) // Fetch days for each calendar
                                              .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskCalendarDto> getCalendarById(@PathVariable Integer id) {
        Optional<TaskCalendar> calendarOptional = taskCalendarDao.findCalendarById(id);
        return calendarOptional.map(cal -> ResponseEntity.ok(convertCalendarToDto(cal, true)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Changed from /group/{groupName} to /name/{calendarName} for clarity based on schema
    @GetMapping("/name/{calendarName}")
    public ResponseEntity<TaskCalendarDto> getCalendarByName(@PathVariable String calendarName) {
        Optional<TaskCalendar> calendarOptional = taskCalendarDao.findCalendarByName(calendarName);
        return calendarOptional.map(cal -> ResponseEntity.ok(convertCalendarToDto(cal, true)))
                .orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCalendar(@PathVariable Integer id) {
        if (!taskCalendarDao.findCalendarById(id).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        // DAO implementation already handles deleting associated days
        taskCalendarDao.deleteCalendarById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Calendar Day Endpoints (within a specific calendar) ---
    // These could be nested routes like /embed-api/calendars/{calendarId}/days

    @PostMapping("/{calendarId}/days")
    public ResponseEntity<TaskCalendarDayDto> addDayToCalendar(@PathVariable Integer calendarId, @RequestBody TaskCalendarDayDto dayDto) {
        if (!taskCalendarDao.findCalendarById(calendarId).isPresent()) {
            return ResponseEntity.notFound().build(); // Calendar not found
        }
        if (dayDto == null || dayDto.getEventDate() == null) {
            return ResponseEntity.badRequest().build();
        }
        // Check if day already exists for this calendar
        if (taskCalendarDao.findCalendarDayByCalendarIdAndDate(calendarId, dayDto.getEventDate()).isPresent()){
            return ResponseEntity.status(HttpStatus.CONFLICT).build(); // Day already exists
        }

        TaskCalendarDay day = convertDayDtoToEntity(dayDto);
        day.setCalendarId(calendarId);
        day.setDayId(null); // Ensure new
        TaskCalendarDay savedDay = taskCalendarDao.saveCalendarDay(day);
        return ResponseEntity.status(HttpStatus.CREATED).body(convertDayToDto(savedDay));
    }

    @GetMapping("/{calendarId}/days")
    public ResponseEntity<List<TaskCalendarDayDto>> getDaysForCalendar(@PathVariable Integer calendarId) {
        if (!taskCalendarDao.findCalendarById(calendarId).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        List<TaskCalendarDay> days = taskCalendarDao.findCalendarDaysByCalendarId(calendarId);
        List<TaskCalendarDayDto> dtos = days.stream().map(this::convertDayToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{calendarId}/days/{dayId}")
    public ResponseEntity<Void> removeDayFromCalendar(@PathVariable Integer calendarId, @PathVariable Integer dayId) {
        // Optional: check if dayId actually belongs to calendarId before deleting
        Optional<TaskCalendarDay> dayOptional = taskCalendarDao.findCalendarDayById(dayId);
        if (!dayOptional.isPresent() || dayOptional.get().getCalendarId() != calendarId) {
            return ResponseEntity.notFound().build();
        }
        taskCalendarDao.deleteCalendarDayById(dayId);
        return ResponseEntity.noContent().build();
    }
}
