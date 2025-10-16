package com.github.embed.scheduler.service;

import com.github.embed.scheduler.entity.TaskConfig;

public interface TaskExecutor {
    String execute(TaskConfig taskConfig, Long logId) throws Exception;
}
