package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.dao.TaskConfigDao; // Assuming this is needed for task names if not in log DAO results
import com.example.taskscheduler.entity.TaskConfig; // For fetching task details if needed
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class TaskStatisticsService {

    private static final Logger logger = LoggerFactory.getLogger(TaskStatisticsService.class);

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;

    @Autowired
    private TaskConfigDao taskConfigDao; // To fetch task names if not directly available

    /**
     * Gets global execution state counts (SUCCESS, FAILED, RUNNING, etc.).
     * Transforms DAO result: List<Map<String, Object>> [{state: "SUCCESS", count: 10}]
     * into Map<String, Long> {"SUCCESS": 10L}.
     * @return A map where keys are states and values are their counts.
     */
    public Map<String, Long> getGlobalExecutionStateCounts() {
        List<Map<String, Object>> rawCounts = taskExecuteLogDao.getOverallStatusCounts(); // Reusing existing DAO method
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rawCounts) {
            String state = (String) row.get("state");
            // The count might be Integer, Long, or BigDecimal depending on DB and JDBC driver
            Object countValue = row.get("count");
            if (state != null && countValue instanceof Number) {
                counts.put(state, ((Number) countValue).longValue());
            } else if (state != null) {
                try {
                    // Fallback if it's a String that can be parsed (less likely for COUNT(*))
                    counts.put(state, Long.parseLong(String.valueOf(countValue)));
                } catch (NumberFormatException e) {
                    logger.warn("Could not parse count value for state {}: {}", state, countValue);
                }
            }
        }
        // Ensure all typical states are present, even if count is 0
        // Add other states if relevant: "RUNNING", "TIMED_OUT", "CANCELLED"
        counts.putIfAbsent("SUCCESS", 0L);
        counts.putIfAbsent("FAILED", 0L);
        counts.putIfAbsent("RUNNING", 0L); // Assuming "RUNNING" is a possible state in logs

        return counts;
    }

    /**
     * Gets per-task statistics including success and failure counts.
     * Uses the new DAO method getPerTaskSuccessFailureCounts().
     * @return A list of maps, where each map contains task_id, task_name, success_count, failed_count.
     */
    public List<Map<String, Object>> getTaskBreakdownStatistics() {
        // This DAO method already joins with task_config to get task_name
        return taskExecuteLogDao.getPerTaskSuccessFailureCounts();
    }

    /**
     * Gets the top N tasks by average execution time for successful runs.
     * Uses the new DAO method getTopNAverageExecutionTimes().
     * The DAO method already includes task_id and task_name.
     * @param limit The number of top tasks to retrieve.
     * @return A list of maps, where each map contains task_id, task_name, and avg_duration_ms.
     */
    public List<Map<String, Object>> getTopNTasksByAverageExecutionTime(int limit) {
        if (limit <= 0) {
            limit = 5; // Default to a sensible limit
        }
        return taskExecuteLogDao.getTopNAverageExecutionTimes(limit);
    }

    // You can add more service methods here, for example:
    // - Get most frequently failing tasks
    // - Get tasks with no recent successful executions
    // - Get execution time trends for a specific task
}
