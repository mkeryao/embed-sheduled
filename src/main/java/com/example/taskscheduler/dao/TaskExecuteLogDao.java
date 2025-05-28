package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskExecuteLog;
import java.util.List;
import java.util.Optional;

public interface TaskExecuteLogDao {
    TaskExecuteLog save(TaskExecuteLog log);
    Optional<TaskExecuteLog> findById(Integer logId);
    List<TaskExecuteLog> findAll();
    List<TaskExecuteLog> findByTaskId(Integer taskId);
    int update(TaskExecuteLog log);
    // Specific update for state, end_time, and ex_msg, as this is common
    void updateLogStatus(Integer logId, String state, String exMsg);
}
