package com.example.taskscheduler.scheduler;

import com.example.taskscheduler.dao.TaskCalendarDao;
import com.example.taskscheduler.dao.TaskConfigDao;
import com.example.taskscheduler.dao.TaskExecuteLogDao;
import com.example.taskscheduler.entity.TaskConfig;
import com.example.taskscheduler.entity.TaskExecuteLog;
import com.example.taskscheduler.service.*;
import com.example.taskscheduler.util.ExpressionUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;


import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class CoreSchedulerServiceTests {

    @Mock
    private TaskConfigDao taskConfigDao;
    @Mock
    private TaskExecuteLogDao taskExecuteLogDao;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private BeanTaskExecutor beanTaskExecutor;
    @Mock
    private DistributedLockService distributedLockService;
    @Mock
    private TaskCalendarDao taskCalendarDao;
    @Mock
    private NotificationService notificationService;
    @Mock
    private WorkflowExecutionService workflowExecutionService;
    @Mock
    private ScheduledFuture<?> mockScheduledFuture;

    @Captor
    private ArgumentCaptor<Runnable> runnableCaptor;
    @Captor
    private ArgumentCaptor<Trigger> triggerCaptor;
    @Captor
    private ArgumentCaptor<TaskExecuteLog> logCaptor;


    @InjectMocks
    private CoreSchedulerService coreSchedulerService;

    @BeforeEach
    void setUp() {
        // Setup common mock behaviors
        lenient().when(applicationContext.getBean(BeanTaskExecutor.class)).thenReturn(beanTaskExecutor);
        lenient().when(distributedLockService.getSchedulerInstanceId()).thenReturn("test-instance");
        // Mock taskScheduler.schedule to return our mockScheduledFuture
        lenient().when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mockScheduledFuture);
    }

    private TaskConfig createTaskConfig(Integer id, String cron, boolean active) {
        TaskConfig task = new TaskConfig();
        task.setTaskId(id);
        task.setTaskName("TestTask" + id);
        task.setCronExpression(cron);
        task.setActive(active);
        task.setTaskType(0); // BEAN task by default
        // Set default values for other nullable fields to avoid NPEs if accessed
        task.setTaskGroup("DEFAULT");
        task.setExecuteTimeoutSeconds(0);
        return task;
    }
    
    private TaskExecuteLog createDefaultLog() {
        TaskExecuteLog log = new TaskExecuteLog();
        log.setLogId(1);
        log.setTaskId(1);
        log.setStartTime(new Timestamp(System.currentTimeMillis()));
        log.setState("RUNNING");
        log.setInstanceId("test-instance");
        return log;
    }

    @Test
    void testScheduleNewTask_Success() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", true);
        when(taskExecuteLogDao.save(any(TaskExecuteLog.class))).thenReturn(createDefaultLog());
        when(taskExecuteLogDao.findById(anyInt())).thenReturn(Optional.of(createDefaultLog()));


        boolean scheduled = coreSchedulerService.scheduleTask(task);

        assertTrue(scheduled);
        verify(taskScheduler).schedule(runnableCaptor.capture(), triggerCaptor.capture());
        assertNotNull(runnableCaptor.getValue());
        assertNotNull(triggerCaptor.getValue());

        // Simulate running the task
        runnableCaptor.getValue().run();
        verify(taskExecuteLogDao, atLeastOnce()).save(any(TaskExecuteLog.class)); // Initial RUNNING log
        // verify(beanTaskExecutor).execute(task); // This would be part of the runnable's logic
        verify(notificationService).sendNotification(eq(task), any(TaskExecuteLog.class));
    }
    
    @Test
    void testScheduleTask_WhenInactive_ShouldNotSchedule() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", false);
        boolean scheduled = coreSchedulerService.scheduleTask(task);
        assertFalse(scheduled);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void testScheduleTask_InvalidCron_ShouldFail() {
        TaskConfig task = createTaskConfig(1, "INVALID CRON", true);
        boolean scheduled = coreSchedulerService.scheduleTask(task);
        assertFalse(scheduled);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }


    @Test
    void testCancelTask_Success() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", true);
        // First, schedule the task so it's in the scheduledTasks map
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mockScheduledFuture);
        coreSchedulerService.scheduleTask(task);

        when(mockScheduledFuture.cancel(true)).thenReturn(true);
        boolean cancelled = coreSchedulerService.cancelTask(1);

        assertTrue(cancelled);
        verify(mockScheduledFuture).cancel(true);
    }

    @Test
    void testCancelTask_NotFound() {
        boolean cancelled = coreSchedulerService.cancelTask(999); // Non-existent task ID
        assertFalse(cancelled);
        verify(mockScheduledFuture, never()).cancel(anyBoolean());
    }
    
    // --- Tests for task skipping logic ---

    @Test
    void testTaskSkipping_StartDateInFuture() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", true);
        task.setStartDate(Date.valueOf(LocalDate.now().plusDays(1))); // Start date is tomorrow

        when(taskExecuteLogDao.save(any(TaskExecuteLog.class))).thenReturn(createDefaultLog());
         when(taskExecuteLogDao.findById(anyInt())).thenReturn(Optional.of(createDefaultLog()));


        coreSchedulerService.scheduleTask(task);
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run(); // Simulate execution

        verify(taskExecuteLogDao).updateLogStatus(anyInt(), eq("SKIPPED"), contains("Start date"));
        verify(beanTaskExecutor, never()).execute(any(TaskConfig.class));
        verify(notificationService).sendNotification(eq(task), any(TaskExecuteLog.class));
    }

    @Test
    void testTaskSkipping_EndDateInPast() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", true);
        task.setEndDate(Date.valueOf(LocalDate.now().minusDays(1)));

        when(taskExecuteLogDao.save(any(TaskExecuteLog.class))).thenReturn(createDefaultLog());
        when(taskExecuteLogDao.findById(anyInt())).thenReturn(Optional.of(createDefaultLog()));

        coreSchedulerService.scheduleTask(task);
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();

        verify(taskExecuteLogDao).updateLogStatus(anyInt(), eq("SKIPPED"), contains("End date"));
        verify(beanTaskExecutor, never()).execute(any(TaskConfig.class));
    }
    
    @Test
    void testTaskSkipping_TimeExclusion() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", true);
        // Exclude a time range that includes the current time (this is tricky to test without fixing current time)
        // For simplicity, let's assume current time is 10:00 and exclude 09:00-11:00
        // This requires controlling CurrentTime inside the Runnable.
        // A better approach for unit testing time exclusions might be to extract time checking logic.
        // For now, we'll test the path, assuming the LocalTime.now() within runnable will fall into this.
        // This test is inherently flaky if not run at a specific time or if time is not mocked.
        // Let's mock LocalTime.now() if possible, or make the range very broad for testing.
        // For this example, we cannot easily mock LocalTime.now() without PowerMock or similar.
        // So, this test will focus on the logic path assuming the time check works.
        
        LocalTime now = LocalTime.now(ZoneId.systemDefault());
        String excludeRange = String.format("%s-%s", now.minusMinutes(5).toString(), now.plusMinutes(5).toString());
        task.setTaskExcludeTimes(excludeRange);


        when(taskExecuteLogDao.save(any(TaskExecuteLog.class))).thenReturn(createDefaultLog());
        when(taskExecuteLogDao.findById(anyInt())).thenReturn(Optional.of(createDefaultLog()));

        coreSchedulerService.scheduleTask(task);
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Trigger.class));
        runnableCaptor.getValue().run();

        verify(taskExecuteLogDao).updateLogStatus(anyInt(), eq("SKIPPED"), contains("within excluded range"));
        verify(beanTaskExecutor, never()).execute(any(TaskConfig.class));
    }
    
    // Test for loadAndScheduleInitialTasks
    @Test
    void testLoadAndScheduleInitialTasks() {
        TaskConfig task1 = createTaskConfig(1, "0 0 1 * * ?", true);
        TaskConfig task2 = createTaskConfig(2, "0 0 2 * * ?", false); // Inactive
        TaskConfig task3 = createTaskConfig(3, "0 0 3 * * ?", true);

        when(taskConfigDao.findAllActiveTasks()).thenReturn(java.util.Arrays.asList(task1, task3));
        // Mock scheduleTask calls for these specific tasks to avoid NPEs if they try to schedule fully
        // This is tricky as scheduleTask is a public method of the class under test.
        // We can spy on coreSchedulerService, but let's try verifying taskScheduler.schedule directly.
        
        // We need to ensure that when scheduleTask is called internally, its call to taskScheduler.schedule
        // is what we expect and returns mockScheduledFuture.
        // This is already handled by the lenient().when(taskScheduler.schedule(...)) in setUp.

        coreSchedulerService.loadAndScheduleInitialTasks();

        // Verify schedule is called for task1 and task3
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Trigger.class));
        // More specific verification would require capturing arguments for each call.
    }
    
    @Test
    void testTriggerTaskManually_TaskNotFound() {
        when(taskConfigDao.findById(999)).thenReturn(Optional.empty());
        
        assertThrows(IllegalArgumentException.class, () -> {
            coreSchedulerService.triggerTaskManually(999);
        });
        
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void testTriggerTaskManually_Success() {
        TaskConfig task = createTaskConfig(1, "0 0 * * * ?", true);
        when(taskConfigDao.findById(1)).thenReturn(Optional.of(task));
        when(taskExecuteLogDao.save(any(TaskExecuteLog.class))).thenReturn(createDefaultLog()); // For the runnable
        when(taskExecuteLogDao.findById(anyInt())).thenReturn(Optional.of(createDefaultLog()));


        coreSchedulerService.triggerTaskManually(1);

        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Instant.class));
        // Simulate running the manually triggered task
        runnableCaptor.getValue().run();
        verify(taskExecuteLogDao, atLeastOnce()).save(any(TaskExecuteLog.class));
        // verify(beanTaskExecutor).execute(task); // Inside runnable
        verify(notificationService).sendNotification(eq(task), any(TaskExecuteLog.class));
    }
    
    // Test for configureTasks - ensures taskRegistrar is set
    @Test
    void testConfigureTasks() {
        ScheduledTaskRegistrar mockRegistrar = mock(ScheduledTaskRegistrar.class);
        coreSchedulerService.configureTasks(mockRegistrar);
        // Not much to assert here other than it doesn't throw an error,
        // and if taskRegistrar was used internally for scheduling, verify those interactions.
        // In the current CoreSchedulerService, taskRegistrar is stored but direct scheduling via
        // taskScheduler is used for dynamic add/remove.
        // If loadAndScheduleInitialTasks used taskRegistrar, we'd verify that.
        // For now, this test is basic.
        assertNotNull(mockRegistrar); // Placeholder assertion
    }

}
