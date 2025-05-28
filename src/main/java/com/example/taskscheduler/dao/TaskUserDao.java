package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskUser;
import java.util.List;
import java.util.Optional;

public interface TaskUserDao {
    TaskUser save(TaskUser user);
    Optional<TaskUser> findById(Integer userId);
    Optional<TaskUser> findByUsername(String username);
    List<TaskUser> findAll();
    int update(TaskUser user);
    int deleteById(Integer userId);
}
