package com.example.taskscheduler.dao;

import com.example.taskscheduler.entity.TaskExecuteLog;
import java.util.List;
import java.util.Map; // Added import for Map
import java.util.Optional;

/**
 * Data Access Object interface for {@link TaskExecuteLog} entities.
 * Defines methods for creating, retrieving, and updating task execution logs.
 */
public interface TaskExecuteLogDao {
    /**
     * Saves a new task execution log entry.
     * @param log The log entry to save.
     * @return The saved log entry, typically with a generated ID.
     */
    TaskExecuteLog save(TaskExecuteLog log);

    /**
     * Finds a task execution log by its ID.
     * @param logId The ID of the log entry.
     * @return An {@link Optional} containing the log entry if found, or empty otherwise.
     */
    Optional<TaskExecuteLog> findById(Integer logId);

    /**
     * Retrieves all task execution logs.
     * Note: For large datasets, this method should be used with caution or be replaced
     * by a paginated version.
     * @return A list of all task execution logs.
     */
    List<TaskExecuteLog> findAll();

    /**
     * Finds all log entries associated with a specific task ID.
     * @param taskId The ID of the task.
     * @return A list of log entries for the specified task.
     */
    List<TaskExecuteLog> findByTaskId(Integer taskId);

    /**
     * Updates an existing task execution log entry.
     * @param log The log entry with updated values.
     * @return The number of rows affected.
     */
    int update(TaskExecuteLog log);

    /**
     * Specifically updates the status of a log entry, typically at the end of a task execution.
     * Sets the end time to the current timestamp, and updates the state and exception message.
     *
     * @param logId The ID of the log entry to update.
     * @param state The final state of the task execution (e.g., SUCCESS, FAILED, TIMED_OUT).
     * @param exMsg An optional exception message if the task failed or timed out.
     */
    void updateLogStatus(Integer logId, String state, String exMsg);

    // --- Statistics Methods ---
    /**
     * Gets the total counts for each execution state (SUCCESS, FAILED, etc.) across all logs.
     * @return A list of maps, where each map has "state" and "count" keys.
     */
    List<Map<String, Object>> getOverallStatusCounts();

    /**
     * Gets the counts for each execution state for a specific task.
     * @param taskId The ID of the task.
     * @return A list of maps, where each map has "state" and "count" keys for the given task.
     */
    List<Map<String, Object>> getTaskStatusCounts(Integer taskId);

    /**
     * Gets the top N most frequently executed tasks.
     * @param n The number of top tasks to retrieve.
     * @return A list of maps, where each map contains "task_id", "task_name" (from task_config), and "execution_count".
     */
    List<Map<String, Object>> getTopNExecutedTasks(int n);

    /**
     * Calculates the average execution time (endTime - startTime) in milliseconds for successful runs of a specific task.
     * @param taskId The ID of the task.
     * @return The average execution time in milliseconds, or null if no successful runs are found or times are invalid.
     */
    Double getAverageExecutionTime(Integer taskId);

    /**
     * Gets the total number of runs (log entries) for a specific task.
     * @param taskId The ID of the task.
     * @return The total number of runs.
     */
    Long getTotalRuns(Integer taskId);

    // --- New Statistics Methods for Point 8 ---

    /**
     * Gets counts of SUCCESS and FAILED executions for each task.
     * Includes task_id and task_name.
     * @return List of maps, e.g., {task_id, task_name, success_count, failed_count}
     */
    List<Map<String, Object>> getPerTaskSuccessFailureCounts();

    /**
     * Gets the top N tasks by average execution time (in milliseconds) for successful runs.
     * Includes task_id, task_name, and avg_duration_ms.
     * @param limit The number of top tasks to retrieve.
     * @return List of maps, e.g., {task_id, task_name, avg_duration_ms}
     */
    List<Map<String, Object>> getTopNAverageExecutionTimes(int limit);

    /**
     * Finds all log entries where parent_execute_no matches the given ID.
     * This is used to retrieve all node execution logs for a specific workflow instance.
     * @param parentExecuteNo The log_id of the parent workflow execution.
     * @return A list of child log entries.
     */
    List<TaskExecuteLog> findByParentExecuteNo(long parentExecuteNo);
}
