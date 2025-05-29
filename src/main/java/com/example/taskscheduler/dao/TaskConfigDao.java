package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskConfig;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for {@link TaskConfig} entities.
 * Defines methods for CRUD operations and specific queries related to task configurations.
 */
public interface TaskConfigDao {
    /**
     * Saves a new task configuration or updates an existing one.
     * If the taskConfig has a null ID, it's treated as a new entity for insertion.
     * Otherwise, it's an update to an existing entity.
     *
     * @param taskConfig The task configuration to save.
     * @return The saved task configuration, potentially with an updated ID if it was a new entity.
     */
    TaskConfig save(TaskConfig taskConfig);

    /**
     * Finds a task configuration by its ID.
     * @param taskId The ID of the task configuration.
     * @return An {@link Optional} containing the task configuration if found, or empty otherwise.
     */
    Optional<TaskConfig> findById(Integer taskId);
    /**
     * Retrieves all task configurations.
     * @return A list of all task configurations.
     */
    List<TaskConfig> findAll();

    /**
     * Retrieves all active task configurations (where {@code isActive} is true).
     * @return A list of active task configurations.
     */
    List<TaskConfig> findAllActiveTasks();

    /**
     * Finds a task configuration by its group and name.
     * @param taskGroup The group of the task.
     * @param taskName The name of the task.
     * @return An {@link Optional} containing the task configuration if found, or empty otherwise.
     */
    Optional<TaskConfig> findByTaskGroupAndTaskName(String taskGroup, String taskName);

    /**
     * Updates an existing task configuration.
     * @param taskConfig The task configuration with updated values.
     * @return The number of rows affected (should be 1 if successful, 0 otherwise).
     */
    int update(TaskConfig taskConfig);

    /**
     * Deletes a task configuration by its ID.
     * @param taskId The ID of the task configuration to delete.
     * @return The number of rows affected.
     */
    int deleteById(Integer taskId);

    /**
     * Updates the active status of a specific task.
     * @param taskId The ID of the task to update.
     * @param isActive The new active status (true for active, false for inactive).
     */
    void updateTaskStatus(Integer taskId, boolean isActive);
}
