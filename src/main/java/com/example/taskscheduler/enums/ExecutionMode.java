package com.example.taskscheduler.enums;

/**
 * Defines how a task should be executed in a clustered environment.
 * BROADCAST: The task runs on all scheduler instances.
 * CLUSTER: The task attempts to acquire a distributed lock, ensuring only one instance executes it.
 */
public enum ExecutionMode {
    /**
     * The task will be executed on all available scheduler instances in the cluster.
     * This is typically used for tasks that should run on every node, or for tasks that are
     * idempotent and where multiple executions are not harmful (though typically not desired).
     * No distributed lock is acquired for tasks in this mode.
     */
    BROADCAST,

    /**
     * The task will attempt to acquire a distributed lock before execution.
     * Only the scheduler instance that successfully acquires the lock will execute the task.
     * This ensures that the task runs on only one instance in the cluster at any given time.
     * The lock name is automatically derived from the task's unique identifier.
     */
    CLUSTER
}
