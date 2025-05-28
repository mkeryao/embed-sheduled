-- Main table for task configuration
CREATE TABLE task_config (
    task_id INT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    task_group VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    task_type INT NOT NULL COMMENT '0: Bean task, 1: HTTP task (Not Implemented), 2: Shell script (Not Implemented), 3: Workflow task',
    bean_name VARCHAR(255), -- For Bean tasks
    method_name VARCHAR(255), -- For Bean tasks
    bean_parameters TEXT, -- JSON string for parameters
    http_url VARCHAR(1024), -- For HTTP tasks
    http_method VARCHAR(10), -- GET, POST, etc.
    http_headers TEXT, -- JSON string for headers
    http_body TEXT, -- For POST/PUT requests
    script_path VARCHAR(1024), -- For Shell scripts
    script_parameters TEXT, -- Parameters for the script
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    task_lock_name VARCHAR(255) DEFAULT NULL, -- Name of the distributed lock for this task
    task_lock_most_seconds INT DEFAULT NULL, -- Duration for the lock in seconds
    workflow_nodes TEXT, -- JSON array of WorkflowNode
    workflow_edges TEXT, -- JSON array of WorkflowEdge
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
    state VARCHAR(20) NOT NULL COMMENT 'RUNNING, SUCCESS, FAILED, TIMEOUT, SKIPPED',
    ex_msg TEXT, -- Exception message if any
    instance_id VARCHAR(255), -- Identifier of the scheduler instance that ran the task
    parent_execute_no INT, -- For workflow steps, references the main workflow's log_id
    task_pattern VARCHAR(50), -- e.g. NORMAL, WORKFLOW_STEP
    FOREIGN KEY (task_id) REFERENCES task_config(task_id) ON DELETE CASCADE,
    FOREIGN KEY (parent_execute_no) REFERENCES task_execute_log(log_id) ON DELETE SET NULL
);

-- User table for potential UI authentication/authorization or task ownership
CREATE TABLE task_user (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, -- Store hashed passwords only
    email VARCHAR(255),
    webhook_address VARCHAR(1024), -- For notifications
    is_admin BOOLEAN DEFAULT FALSE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Calendar table for non-working days or specific execution dates
CREATE TABLE task_calendar (
    calendar_id INT AUTO_INCREMENT PRIMARY KEY,
    calendar_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE task_calendar_day (
    day_id INT AUTO_INCREMENT PRIMARY KEY,
    calendar_id INT NOT NULL,
    event_date DATE NOT NULL,
    is_working_day BOOLEAN DEFAULT TRUE, -- False for holidays, true for special working days
    description TEXT,
    UNIQUE KEY uk_calendar_date (calendar_id, event_date),
    FOREIGN KEY (calendar_id) REFERENCES task_calendar(calendar_id) ON DELETE CASCADE
);

-- Lock table for distributed task execution (optimistic locking or leader election)
CREATE TABLE task_lock (
    lock_name VARCHAR(255) PRIMARY KEY,
    owner_instance_id VARCHAR(255),
    lock_acquired_time TIMESTAMP,
    lease_duration_ms INT,
    version INT
);

-- Example data
-- Note: The INSERT INTO task_config needs to be updated to include all columns from the modified table def.
-- The previous INSERTs only had 11 columns, now it has more due to advanced features + workflow.
-- For existing tasks, new columns like task_calendar_group, etc., will be NULL.
-- The example workflow task will populate workflow_nodes.
INSERT INTO task_config (
    task_name, task_group, cron_expression, task_type, 
    bean_name, method_name, bean_parameters, 
    http_url, http_method, http_headers, http_body, 
    script_path, script_parameters, 
    description, is_active, 
    task_lock_name, task_lock_most_seconds,
    task_calendar_group, task_exclude_times, start_date, end_date, execute_timeout_seconds,
    notify_success_user_ids, notify_failed_user_ids,
    workflow_nodes, workflow_edges, global_parameters
) VALUES
('MySampleSuccessTask', 'DEFAULT_GROUP', '0/30 * * * * ?', 0, 'mySampleTask', 'executeSuccess', '{"message":"Hello from scheduler!", "value": 123}', NULL, NULL, NULL, NULL, NULL, NULL, 'A sample task that should succeed.', TRUE, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL),
('MySampleFailedTask', 'DEFAULT_GROUP', '0/45 * * * * ?', 0, 'mySampleTask', 'executeFailed', '{"error":"Simulated failure"}', NULL, NULL, NULL, NULL, NULL, NULL, 'A sample task that is expected to fail.', TRUE, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL),
('MyLockedTask', 'DEFAULT_GROUP', '0/20 * * * * ?', 0, 'mySampleTask', 'simpleExecute', '{}', NULL, NULL, NULL, NULL, NULL, NULL, 'A sample task that uses a distributed lock.', TRUE, 'SAMPLE_LOCK_FOR_MY_TASK', 60, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL),
('MyInactiveTask', 'DEFAULT_GROUP', '0 0 0 1 1 ?', 0, 'mySampleTask', 'executeSuccess', '{"message":"This should not run", "value": 0}', NULL, NULL, NULL, NULL, NULL, NULL, 'An inactive sample task.', FALSE, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NULL, NULL),
('MyFirstWorkflow', 'WORKFLOW_GROUP', '0 0 1 * * ?', 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'A sample workflow task.', TRUE, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, 
    '[{"nodeId":"node1","taskConfigId":1,"nodeName":"Step 1: Success Task","parameters":{"message":"Input for step 1"}}, {"nodeId":"node2","taskConfigId":2,"nodeName":"Step 2: Failed Task","parameters":{}}]',
    '[{"fromNodeId":"node1", "toNodeId":"node2", "condition":"SUCCESS", "expression":"${node1_output.status == ''SUCCESS''}"}]',
    '{"global_api_key":"some_global_value", "default_retry_count":3}');

INSERT INTO task_user (username, password_hash, email, webhook_address, is_admin)
VALUES
('admin', 'a79913f77510932813c0077209097628111854514c3805845979118300a281e5', 'admin@example.com', NULL, TRUE);

INSERT INTO task_calendar (calendar_name, description) VALUES ('NATIONAL_HOLIDAYS', 'National holidays for the year.');
INSERT INTO task_calendar_day (calendar_id, event_date, is_working_day, description)
SELECT c.calendar_id, '2024-01-01', FALSE, 'New Year''s Day' FROM task_calendar c WHERE c.calendar_name = 'NATIONAL_HOLIDAYS';
INSERT INTO task_calendar_day (calendar_id, event_date, is_working_day, description)
SELECT c.calendar_id, '2024-12-25', FALSE, 'Christmas Day' FROM task_calendar c WHERE c.calendar_name = 'NATIONAL_HOLIDAYS';

-- Dummy lock for testing
INSERT INTO task_lock (lock_name, owner_instance_id, lock_acquired_time, lease_duration_ms, version)
VALUES ('GLOBAL_SCHEDULER_LOCK', NULL, NULL, 60000, 0);
