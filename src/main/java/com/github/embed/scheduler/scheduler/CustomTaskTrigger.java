package com.github.embed.scheduler.scheduler;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.util.StringUtils;

import com.github.embed.scheduler.dao.TaskCalendarDao;
import com.github.embed.scheduler.entity.TaskConfig;

public class CustomTaskTrigger implements Trigger {

    private static final Logger logger = LoggerFactory.getLogger(CustomTaskTrigger.class);

    private final TaskConfig taskConfig;
    private final TaskCalendarDao taskCalendarDao;
    private final CronTrigger cronTrigger; // Internal CronTrigger

    // Safety limits for finding next execution
    private static final int MAX_ITERATIONS = 6 * 60 * 24; // Max attempts to find a valid time
    private static final int MAX_YEARS_IN_FUTURE = 5; // Max years to look ahead

    public CustomTaskTrigger(TaskConfig taskConfig, TaskCalendarDao taskCalendarDao) {
        this.taskConfig = taskConfig;
        this.taskCalendarDao = taskCalendarDao;
        // Assuming system default time zone if not specified in TaskConfig
        // For production, it's better to have timezone explicitly in TaskConfig or
        // application default
        this.cronTrigger = new CronTrigger(taskConfig.getCronExpression(), TimeZone.getDefault());
    }

    @Override
    public Date nextExecutionTime(TriggerContext triggerContext) {
        Date lastExecutionTime = triggerContext.lastScheduledExecutionTime();
        if (lastExecutionTime == null) {
            lastExecutionTime = triggerContext.lastActualExecutionTime();
        }
        // If both are null, it means this is the first execution after scheduling,
        // or we are predicting from 'now'.
        // For prediction via API, lastScheduledExecutionTime might be set to 'now'.
        Date nextPotentialExecutionTime = (lastExecutionTime != null) ? new Date(lastExecutionTime.getTime() + 1)
                : new Date();

        Calendar maxFutureDate = Calendar.getInstance();
        maxFutureDate.add(Calendar.YEAR, MAX_YEARS_IN_FUTURE);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            // 1. Get next time from CronTrigger
            nextPotentialExecutionTime = cronTrigger.nextExecutionTime(new SimpleTriggerContext(
                    nextPotentialExecutionTime, lastExecutionTime, triggerContext.lastCompletionTime()));

            if (nextPotentialExecutionTime == null) {
                logger.warn("Task ID {}: Cron expression '{}' yielded no further execution times.",
                        taskConfig.getTaskId(), taskConfig.getCronExpression());
                return null; // No more executions as per cron
            }

            // Update lastExecutionTime to the new nextPotentialExecutionTime for next
            // iteration
            lastExecutionTime = nextPotentialExecutionTime;

            if (nextPotentialExecutionTime.after(maxFutureDate.getTime())) {
                logger.warn("Task ID {}: Searched for next execution time beyond {} years. Stopping search.",
                        taskConfig.getTaskId(), MAX_YEARS_IN_FUTURE);
                return null; // Safety break: too far in future
            }

            // Convert to LocalDateTime for easier date/time checks
            LocalDateTime ldt = LocalDateTime.ofInstant(nextPotentialExecutionTime.toInstant(), ZoneId.systemDefault());

            // 2. Check against taskConfig.startDate (now a Timestamp)
            if (taskConfig.getStartDate() != null && nextPotentialExecutionTime.before(taskConfig.getStartDate())) {
                logger.debug("Task ID {}: Candidate time {} is before start datetime {}. Skipping.",
                        taskConfig.getTaskId(), nextPotentialExecutionTime, taskConfig.getStartDate());
                continue;
            }

            // 3. Check against taskConfig.endDate (now a Timestamp)
            if (taskConfig.getEndDate() != null && nextPotentialExecutionTime.after(taskConfig.getEndDate())) {
                logger.warn("Task ID {}: Candidate time {} is after end datetime {}. No further valid executions.",
                        taskConfig.getTaskId(), nextPotentialExecutionTime, taskConfig.getEndDate());
                return null; // No more valid executions
            }

            // 4. Check against taskConfig.taskCalendarGroup
            if (StringUtils.hasText(taskConfig.getTaskCalendarGroup())) {
                java.sql.Date executionSqlDate = new java.sql.Date(nextPotentialExecutionTime.getTime());
                boolean isExcludedByCalendar = taskCalendarDao.findCalendarByName(taskConfig.getTaskCalendarGroup())
                        .flatMap(calendar -> taskCalendarDao
                                .findCalendarDayByCalendarIdAndDate(calendar.getCalendarId(), executionSqlDate))
                        .map(calendarDay -> !calendarDay.isWorkingDay()) // true if it's a non-working day (excluded)
                        .orElse(true); // Not in calendar or is a working day -> not excluded

                if (isExcludedByCalendar) {
                    logger.debug("Task ID {}: Candidate time {} is excluded by calendar group '{}'. Skipping.",
                            taskConfig.getTaskId(), nextPotentialExecutionTime, taskConfig.getTaskCalendarGroup());
                    continue;
                }
            }

            // 5. Check against taskConfig.taskExcludeTimes
            if (StringUtils.hasText(taskConfig.getTaskExcludeTimes())) {
                LocalTime executionLocalTime = ldt.toLocalTime();
                String[] ranges = taskConfig.getTaskExcludeTimes().split(",");
                boolean excludedByTime = false;
                for (String range : ranges) {
                    String[] times = range.trim().split("-");
                    if (times.length == 2) {
                        try {
                            LocalTime excludeStartTime = LocalTime.parse(times[0].trim());
                            LocalTime excludeEndTime = LocalTime.parse(times[1].trim());
                            // Check if executionLocalTime is within [excludeStartTime, excludeEndTime)
                            if (!executionLocalTime.isBefore(excludeStartTime)
                                    && executionLocalTime.isBefore(excludeEndTime)) {
                                excludedByTime = true;
                                break;
                            }
                        } catch (java.time.format.DateTimeParseException e) {
                            logger.warn("Task ID {}: Invalid time format in task_exclude_times: '{}'. Range: {}",
                                    taskConfig.getTaskId(), e.getMessage(), range);
                        }
                    }
                }
                if (excludedByTime) {
                    logger.debug("Task ID {}: Candidate time {} is within excluded time ranges '{}'. Skipping.",
                            taskConfig.getTaskId(), nextPotentialExecutionTime, taskConfig.getTaskExcludeTimes());
                    continue;
                }
            }

            // If all checks pass, this is a valid execution time
            logger.debug("Task ID {}: Found valid next execution time: {}", taskConfig.getTaskId(),
                    nextPotentialExecutionTime);
            return nextPotentialExecutionTime;
        }
        logger.warn("Task ID {}: Could not find a valid next execution time after {} iterations.",
                taskConfig.getTaskId(), MAX_ITERATIONS);
        return null; // Max iterations reached without finding a valid time
    }

    /**
     * Simple TriggerContext implementation for use when the full context is not
     * available (e.g., predicting next execution time from a specific point).
     */
    private static class SimpleTriggerContext implements TriggerContext {

        private final Date lastScheduledExecutionTime;
        private final Date lastActualExecutionTime;
        private final Date lastCompletionTime;

        public SimpleTriggerContext(Date lastScheduledExecutionTime, Date lastActualExecutionTime,
                Date lastCompletionTime) {
            this.lastScheduledExecutionTime = lastScheduledExecutionTime;
            this.lastActualExecutionTime = lastActualExecutionTime;
            this.lastCompletionTime = lastCompletionTime;
        }

        @Override
        public Date lastScheduledExecutionTime() {
            return this.lastScheduledExecutionTime;
        }

        @Override
        public Date lastActualExecutionTime() {
            return this.lastActualExecutionTime;
        }

        @Override
        public Date lastCompletionTime() {
            return this.lastCompletionTime;
        }
    }
}
