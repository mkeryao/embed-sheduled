package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskExecuteLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class TaskExecuteLogController {

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @GetMapping
    public ResponseEntity<List<TaskExecuteLog>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Basic pagination logic (can be improved with Pageable and proper count for total pages)
        // This is a simplified approach. For robust pagination, use Spring Data JPA's Pageable
        // or manually implement limit/offset queries in DAO.
        // Current DAO does not support pagination directly, so this will fetch all and then sublist.
        // This is INEFFICIENT for large datasets.
        List<TaskExecuteLog> allLogs = taskExecuteLogDao.findAll(); // Assuming this returns all logs for now

        int totalLogs = allLogs.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalLogs);

        if (startIndex >= totalLogs) {
            return ResponseEntity.ok(Collections.emptyList()); // Empty list if page is out of bounds
        }

        List<TaskExecuteLog> paginatedLogs = allLogs.subList(startIndex, endIndex);
        // In a real app, also return total pages/elements in a custom response object or headers
        return ResponseEntity.ok(paginatedLogs);
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<TaskExecuteLog>> getLogsByTaskId(
            @PathVariable Integer taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Similar pagination inefficiency note as above.
        List<TaskExecuteLog> taskLogs = taskExecuteLogDao.findByTaskId(taskId);

        int totalLogs = taskLogs.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalLogs);

        if (startIndex >= totalLogs) {
            return ResponseEntity.ok(Collections.emptyList()); // Empty list if page is out of bounds
        }

        List<TaskExecuteLog> paginatedLogs = taskLogs.subList(startIndex, endIndex);
        return ResponseEntity.ok(paginatedLogs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskExecuteLog> getLogById(@PathVariable Integer id) {
        return taskExecuteLogDao.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
