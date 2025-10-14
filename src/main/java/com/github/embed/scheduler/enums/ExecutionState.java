package com.github.embed.scheduler.enums;

public enum ExecutionState {

    SUCCESS,
    FAILED,
    RUNNING,
    TIMED_OUT,
    SKIPPED,
    CANCELLED,
    PENDING,
    COMPLETED,
    READY;

    public String getState() {
        return this.name();
    }

    public String toString() {
        return this.name();
    }
}
