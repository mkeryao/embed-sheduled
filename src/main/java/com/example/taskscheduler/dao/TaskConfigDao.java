package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskConfig;
import java.util.List;
import java.util.Optional;

public interface TaskConfigDao {
    TaskConfig save(TaskConfig taskConfig);
    Optional<TaskConfig> findById(Integer taskId);
    List<TaskConfig> findAll();
    List<TaskConfig> findAllActiveTasks();
    Optional<TaskConfig> findByTaskGroupAndTaskName(String taskGroup, String taskName);
    int update(TaskConfig taskConfig);
    int deleteById(Integer taskId);
    void updateTaskStatus(Integer taskId, boolean isActive);
}
