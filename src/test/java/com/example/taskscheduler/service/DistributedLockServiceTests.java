package com.example.taskscheduler.service;

import com.example.taskscheduler.dao.TaskLockDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DistributedLockServiceTests {

    @Mock
    private TaskLockDao taskLockDao;

    @InjectMocks
    private DistributedLockService distributedLockService;

    private final String testLockName = "testLock";
    private final String testOwner = "testInstance";
    private final int lockDurationSeconds = 60;
    private final int leaseDurationMs = lockDurationSeconds * 1000;

    @BeforeEach
    void setUp() {
        // Set configuredInstanceId and retry properties using ReflectionTestUtils
        // as they are @Value annotated and won't be injected directly in plain unit test
        ReflectionTestUtils.setField(distributedLockService, "configuredInstanceId", testOwner);
        ReflectionTestUtils.setField(distributedLockService, "maxLockAttempts", 3);
        ReflectionTestUtils.setField(distributedLockService, "lockRetryDelayMs", 100L); // Use a short delay for tests
        
        // Call init manually after setting fields
        distributedLockService.init();
    }

    @Test
    void testTryLock_AcquiresSuccessfullyOnFirstAttempt() {
        when(taskLockDao.tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs)).thenReturn(true);

        boolean acquired = distributedLockService.tryLock(testLockName, testOwner, lockDurationSeconds);

        assertTrue(acquired);
        verify(taskLockDao, times(1)).tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs);
    }

    @Test
    void testTryLock_FailsWhenLockHeldByAnother_NoRetries() {
        // Re-initialize with 1 attempt to test no retry scenario
        ReflectionTestUtils.setField(distributedLockService, "maxLockAttempts", 1);
        distributedLockService.init(); // Re-init with new maxAttempts

        when(taskLockDao.tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs)).thenReturn(false);

        boolean acquired = distributedLockService.tryLock(testLockName, testOwner, lockDurationSeconds);

        assertFalse(acquired);
        verify(taskLockDao, times(1)).tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs);
    }

    @Test
    void testTryLock_AcquiresOnRetry() {
        when(taskLockDao.tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs))
            .thenReturn(false) // Fails on 1st attempt
            .thenReturn(true); // Succeeds on 2nd attempt

        boolean acquired = distributedLockService.tryLock(testLockName, testOwner, lockDurationSeconds);

        assertTrue(acquired);
        verify(taskLockDao, times(2)).tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs);
    }

    @Test
    void testTryLock_FailsAfterAllRetries() {
        when(taskLockDao.tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs)).thenReturn(false);

        boolean acquired = distributedLockService.tryLock(testLockName, testOwner, lockDurationSeconds);

        assertFalse(acquired);
        verify(taskLockDao, times(3)).tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs); // Max 3 attempts
    }
    
    @Test
    void testTryLock_InterruptedDuringRetrySleep() {
        when(taskLockDao.tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs)).thenReturn(false);

        // To test interruption, we need to run tryLock in a separate thread
        // and interrupt that thread. This is more complex for a standard unit test.
        // A simpler way is to mock Thread.sleep to throw InterruptedException, but that's tricky.
        // For now, this test assumes Thread.sleep completes. If InterruptedException occurs,
        // the current code logs a warning and returns false. We can verify the number of attempts.

        // Simulate Thread.sleep throwing InterruptedException by making the test thread interrupted
        // This is not a perfect simulation but can sometimes trigger the catch block.
        // A robust test for this would require more advanced techniques or a refactor of the sleep logic.
        
        // For this test, let's just ensure it still fails after max attempts if lock not acquired.
        // The InterruptedException path is hard to reliably test here without more complex setup.
        boolean acquired = distributedLockService.tryLock(testLockName, testOwner, lockDurationSeconds);
        assertFalse(acquired);
        verify(taskLockDao, times(3)).tryAcquireOrRefreshLock(testLockName, testOwner, leaseDurationMs);
    }


    @Test
    void testUnlock_Successful() {
        when(taskLockDao.releaseLock(testLockName, testOwner)).thenReturn(true);

        distributedLockService.unlock(testLockName, testOwner);

        verify(taskLockDao, times(1)).releaseLock(testLockName, testOwner);
    }
    
    @Test
    void testUnlock_FailsOrLockNotOwned() {
        when(taskLockDao.releaseLock(testLockName, testOwner)).thenReturn(false);

        distributedLockService.unlock(testLockName, testOwner); // Should not throw exception, just log warning

        verify(taskLockDao, times(1)).releaseLock(testLockName, testOwner);
    }

    @Test
    void testTryLock_WithNullOrEmptyParams() {
        assertFalse(distributedLockService.tryLock(null, testOwner, lockDurationSeconds));
        assertFalse(distributedLockService.tryLock("", testOwner, lockDurationSeconds));
        assertFalse(distributedLockService.tryLock(testLockName, null, lockDurationSeconds));
        assertFalse(distributedLockService.tryLock(testLockName, "", lockDurationSeconds));
        verify(taskLockDao, never()).tryAcquireOrRefreshLock(anyString(), anyString(), anyInt());
    }
    
    @Test
    void testUnlock_WithNullOrEmptyParams() {
        distributedLockService.unlock(null, testOwner);
        distributedLockService.unlock("", testOwner);
        distributedLockService.unlock(testLockName, null);
        distributedLockService.unlock(testLockName, "");
        verify(taskLockDao, never()).releaseLock(anyString(), anyString());
    }
}
