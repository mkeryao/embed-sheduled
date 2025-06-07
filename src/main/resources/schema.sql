-- Main table for task configuration
CREATE TABLE task_config (
    task_id INT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    task_group VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    task_type INT NOT NULL COMMENT '0: Bean task, 1: (Legacy/Unused), 2: HTTP task, 4: Shell script task, 10: Workflow task',

    -- Task execution parameters (specific to task_type)
    bean_name VARCHAR(255), -- For Bean, Workflow (indirectly via nodes)
    method_name VARCHAR(255), -- For Bean tasks
    bean_parameters TEXT, -- For Bean (direct params), HTTP (HttpTaskParameters JSON), Shell (ShellTaskParameters JSON), Workflow (step overrides)

    -- Advanced features
    task_calendar_group VARCHAR(255), -- Name of the calendar group to check for exclusion days
    task_exclude_times TEXT, -- Comma-separated time ranges for exclusion, e.g., "00:00-08:00,22:00-23:59"
    start_date DATETIME DEFAULT NULL, -- Task will not run before this date
    end_date DATETIME DEFAULT NULL, -- Task will not run after this date
    execute_timeout_seconds INT DEFAULT 0, -- 0 means no timeout

    -- Notification settings
    notify_success_user_ids VARCHAR(1024), -- Comma-separated user IDs
    notify_failed_user_ids VARCHAR(1024), -- Comma-separated user IDs

    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'BROADCAST' COMMENT 'Execution mode: BROADCAST or CLUSTER',

    -- Retry configuration
    max_retry_attempts INT DEFAULT 0 COMMENT 'Maximum number of retry attempts upon failure (0 means no retries)',
    retry_interval_seconds INT DEFAULT 30 COMMENT 'Interval in seconds between retry attempts',

    -- Workflow specific fields (for task_type=10)
    workflow_nodes TEXT, -- JSON array of WorkflowNode, defines the structure
    workflow_edges TEXT, -- JSON array of WorkflowEdge, defines transitions
    global_parameters TEXT, -- JSON map for global workflow parameters

    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_group_name (task_group, task_name)
);

-- Task execution log
CREATE TABLE task_execute_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    state VARCHAR(20) NOT NULL COMMENT 'RUNNING, SUCCESS, FAILED, TIMED_OUT, SKIPPED',
    rtn_msg TEXT, -- Return message or short summary of execution
    ex_msg TEXT, -- Exception message if any
    instance_id VARCHAR(255), -- Identifier of the scheduler instance that ran the task
    parent_execute_no INT, -- For workflow steps, references the main workflow's log_id
    task_pattern VARCHAR(50), -- e.g. NORMAL, WORKFLOW_PARENT, WORKFLOW_STEP
    workflow_node_id VARCHAR(255) DEFAULT NULL, -- New column for specific node ID in workflow
    FOREIGN KEY (task_id) REFERENCES task_config(task_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_execute_no) REFERENCES task_execute_log(log_id) ON DELETE SET NULL
);

-- User table
CREATE TABLE task_user (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    webhook_address VARCHAR(1024) DEFAULT NULL COMMENT 'Webhook URL for user notifications. Can be a single URL or a JSON array of URLs.',
    notification_preferences_json TEXT DEFAULT NULL COMMENT 'User notification preferences as JSON',
    is_admin BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Calendar table
CREATE TABLE task_calendar (
    calendar_id INT AUTO_INCREMENT PRIMARY KEY,
    calendar_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE task_calendar_day (
    day_id INT AUTO_INCREMENT PRIMARY KEY,
    calendar_id INT NOT NULL,
    event_date DATE NOT NULL,
    is_working_day BOOLEAN DEFAULT TRUE,
    description TEXT,
    UNIQUE KEY uk_calendar_date (calendar_id, event_date),
    FOREIGN KEY (calendar_id) REFERENCES task_calendar(calendar_id) ON DELETE CASCADE
);

-- Lock table
CREATE TABLE task_lock (
    lock_name VARCHAR(255) PRIMARY KEY,
    owner_instance_id VARCHAR(255),
    lock_acquired_time TIMESTAMP,
    lease_duration_ms INT,
    version INT
);

-- Example data
INSERT INTO task_config (
    task_name, task_group, cron_expression, task_type,
    bean_name, method_name, bean_parameters,
    description, is_active, execution_mode,
    max_retry_attempts, retry_interval_seconds, -- Added new columns
    task_calendar_group, task_exclude_times, start_date, end_date, execute_timeout_seconds,
    notify_success_user_ids, notify_failed_user_ids,
    workflow_nodes, workflow_edges, global_parameters
) VALUES
('MySampleSuccessTask', 'DEFAULT_GROUP', '0/30 * * * * ?', 0, 'mySampleTask', 'executeSuccess', '{"message":"Hello from scheduler!", "value": 123}', 'A sample task that should succeed.', TRUE, 'BROADCAST', 0, 30, NULL, NULL, NULL, NULL, 0, '1', '1', NULL, NULL, NULL),
('MySampleFailedTask', 'DEFAULT_GROUP', '0/45 * * * * ?', 0, 'mySampleTask', 'executeFailed', '{"error":"Simulated failure"}', 'A sample task that is expected to fail.', TRUE, 'BROADCAST', 3, 60, NULL, NULL, '2023-01-01 00:00:00', '2024-12-31 23:59:59', 30, NULL, '1', NULL, NULL, NULL),
('MyClusteredTask', 'DEFAULT_GROUP', '0/20 * * * * ?', 0, 'mySampleTask', 'simpleExecute', '{}', 'A sample task that runs in CLUSTER mode.', TRUE, 'CLUSTER', 0, 30, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL),
('MyInactiveTask', 'DEFAULT_GROUP', '0 0 0 1 1 ?', 0, 'mySampleTask', 'executeSuccess', '{"message":"This should not run", "value": 0}', 'An inactive sample task.', FALSE, 'BROADCAST', 0, 30, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL),
('MyFirstWorkflow', 'WORKFLOW_GROUP', '0 0 1 * * ?', 10, NULL, NULL, NULL, 'A sample workflow task.', TRUE, 'BROADCAST', 0, 30, NULL, NULL, NULL, NULL, 0, NULL, '1',
    '[{"nodeId":"node1","taskConfigId":1,"nodeName":"Step 1: Success Task","parameters":{"message":"Input for step 1 from workflow"}}, {"nodeId":"node2","taskConfigId":2,"nodeName":"Step 2: Failed Task","parameters":{}}]',
    '[{"fromNodeId":"node1", "toNodeId":"node2", "priority":0, "expression":"${node1_status} == ''SUCCESS''"}]',
    '{"global_api_key":"some_global_value", "default_retry_count":3}'),
('SampleHttpTask', 'HTTP_TASKS', '0 0 2 * * ?', 2, NULL, NULL,
    '{"url":"https://jsonplaceholder.typicode.com/todos/1", "method":"GET", "headers":{"X-Custom":"Test"}, "body":null, "connectTimeout":5000, "readTimeout":10000}',
    'A sample HTTP GET task.', TRUE, 'BROADCAST', 2, 45, NULL, NULL, NULL, NULL, 0, '1', '1', NULL, NULL, NULL),
('SampleShellScriptByPath', 'SHELL_TASKS', '0 0 3 * * ?', 4, NULL, NULL,
    '{"script":"/opt/scripts/my_script.sh", "isInlineScript":false, "arguments":["param1","param2"], "workingDirectory":"/opt/scripts"}',
    'A sample path-based Shell script task.', TRUE, 'BROADCAST', 0, 30, NULL, NULL, NULL, NULL, 0, '1', '1', NULL, NULL, NULL),
('SampleInlineShellScript', 'SHELL_TASKS', '0 0 4 * * ?', 4, NULL, NULL,
    '{"script":"#!/bin/bash\\necho \\"Hello from Inline Shell Task! Argument: $1\\"; date\\necho PWD is $(pwd)\\necho USER is $(whoami)", "isInlineScript":true, "arguments":["InlineArg"], "workingDirectory":"/tmp"}',
    'A sample inline Shell script task.', TRUE, 'BROADCAST', 0, 30, NULL, NULL, NULL, NULL, 0, '1', '1', NULL, NULL, NULL);


INSERT INTO task_user (username, password_hash, email, webhook_address, is_admin)
VALUES
('admin', 'a79913f77510932813c0077209097628111854514c380584579118300a281e5', 'admin@example.com', 'https://webhook.site/your-unique-webhook-url-for-admin', TRUE),
('user1', 'another_hashed_password', 'user1@example.com', 'https://webhook.site/your-unique-webhook-url-for-user1', FALSE);

INSERT INTO task_calendar (calendar_name, description) VALUES ('NATIONAL_HOLIDAYS', 'National holidays for the year.');
INSERT INTO task_calendar_day (calendar_id, event_date, is_working_day, description)
SELECT c.calendar_id, '2024-01-01', FALSE, 'New Year''s Day' FROM task_calendar c WHERE c.calendar_name = 'NATIONAL_HOLIDAYS';
INSERT INTO task_calendar_day (calendar_id, event_date, is_working_day, description)
SELECT c.calendar_id, '2024-12-25', FALSE, 'Christmas Day' FROM task_calendar c WHERE c.calendar_name = 'NATIONAL_HOLIDAYS';

INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version)
VALUES ('GLOBAL_SCHEDULER_LOCK', NULL, NULL, 60000, 0);
