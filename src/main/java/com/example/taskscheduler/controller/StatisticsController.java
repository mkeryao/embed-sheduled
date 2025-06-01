package com.example.taskscheduler.controller;

import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for providing statistical data about task execution.
 * This controller offers endpoints to retrieve aggregated information 
 * about task execution patterns, success rates, and distribution across types.
 */
@RestController
@RequestMapping("/api/tasks/statistics")
public class StatisticsController {

    @Autowired
    private TaskExecuteLogDao taskExecuteLogDao;
    
    @Autowired
    private TaskConfigDao taskConfigDao;

    /**
     * Provides comprehensive statistics about task execution.
     * 
     * @return A map containing various statistics like total tasks, today's executions,
     *         success rate, recent failed tasks, task type distribution, and execution status distribution.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        // 获取总任务数
        statistics.put("totalTasks", taskConfigDao.countAllTasks());
        
        // 今日执行次数
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        Timestamp today = Timestamp.valueOf(startOfDay);
        statistics.put("todayExecutions", taskExecuteLogDao.countExecutionsSince(today));
        
        // 获取成功率 (基于最近30天数据)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
        Timestamp thirtyDaysAgoTs = Timestamp.valueOf(thirtyDaysAgo);
        
        List<Map<String, Object>> statusCounts = taskExecuteLogDao.getStatusCountsSince(thirtyDaysAgoTs);
        long totalExecutions = 0;
        long successCount = 0;
        
        for (Map<String, Object> entry : statusCounts) {
            String state = (String) entry.get("state");
            Long count = ((Number) entry.get("count")).longValue();
            
            totalExecutions += count;
            if ("SUCCESS".equals(state)) {
                successCount = count;
            }
        }
        
        int successRate = totalExecutions > 0 ? (int) ((successCount * 100) / totalExecutions) : 0;
        statistics.put("successRate", successRate);
        
        // 最近失败的任务数
        List<Map<String, Object>> failedTaskCounts = taskExecuteLogDao.getRecentFailedTaskCount();
        statistics.put("recentFailedTasks", failedTaskCounts.size());
        
        // 任务类型分布
        Map<Integer, Long> taskTypeDistribution = new HashMap<>();
        List<Map<String, Object>> taskTypes = taskConfigDao.getTaskTypeDistribution();
        for (Map<String, Object> entry : taskTypes) {
            Integer taskType = ((Number) entry.get("task_type")).intValue();
            Long count = ((Number) entry.get("count")).longValue();
            taskTypeDistribution.put(taskType, count);
        }
        statistics.put("taskTypeDistribution", taskTypeDistribution);
        
        // 执行状态分布
        Map<String, Long> executionStatusDistribution = new HashMap<>();
        for (Map<String, Object> entry : statusCounts) {
            String state = (String) entry.get("state");
            Long count = ((Number) entry.get("count")).longValue();
            executionStatusDistribution.put(state, count);
        }
        statistics.put("executionStatusDistribution", executionStatusDistribution);

        return ResponseEntity.ok(statistics);
    }
}
