package com.example.taskscheduler.enums;

public enum ExecutionState {

    SUCCESS,
    FAILED,
    RUNNING,
    TIMED_OUT,
    SKIPPED,
    CANCELLED;

    public String getState() {
        return this.name();
    }

}
