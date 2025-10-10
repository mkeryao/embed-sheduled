-- Task configuration table
CREATE TABLE IF NOT EXISTS task_config (
    task_id INT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(255) NOT NULL,
    task_group VARCHAR(255) NOT NULL,
    cron_expression VARCHAR(255) NOT NULL,
    task_type INT NOT NULL,
    bean_name VARCHAR(255),
    method_name VARCHAR(255),
    bean_parameters TEXT,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    execution_mode VARCHAR(50) DEFAULT 'BROADCAST',
    max_retry_attempts INT DEFAULT 0,
    retry_interval_seconds INT DEFAULT 30,
    retry_interval_multiplier FLOAT DEFAULT 1.0,
    task_calendar_group VARCHAR(255),
    task_exclude_times VARCHAR(500),
    start_date TIMESTAMP NULL,
    end_date TIMESTAMP NULL,
    execute_timeout_seconds INT DEFAULT 0,
    notify_success_user_ids VARCHAR(500),
    notify_failed_user_ids VARCHAR(500),
    workflow_nodes TEXT,
    workflow_edges TEXT,
    global_parameters TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_group_name (task_group, task_name)
);

-- Task execution log table
CREATE TABLE IF NOT EXISTS task_execute_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id INT NOT NULL,
    workflow_id INT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    state VARCHAR(50),
    rtn_msg VARCHAR(2000),
    ex_msg TEXT,
    instance_id VARCHAR(255),
    parent_execute_no INT,
    task_pattern VARCHAR(50),
    workflow_node_id VARCHAR(255),
    parameters VARCHAR(4000) NULL,
    INDEX idx_task_id (task_id),
    INDEX idx_start_time (start_time),
    INDEX idx_instance_id (instance_id),
    INDEX idx_parent_execute_no (parent_execute_no)
);

-- Task lock table for distributed execution
CREATE TABLE IF NOT EXISTS task_lock (
    lock_name VARCHAR(255) PRIMARY KEY,
    locked_by VARCHAR(255) NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_expires_at (expires_at)
);

-- Task calendar table for holiday/non-working day management
CREATE TABLE IF NOT EXISTS task_calendar (
    calendar_id INT AUTO_INCREMENT PRIMARY KEY,
    calendar_name VARCHAR(255) NOT NULL UNIQUE,
    calendar_type VARCHAR(50) NOT NULL,
    calendar_data TEXT,
    description TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Task user table for notification management
CREATE TABLE IF NOT EXISTS task_user (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    webhook_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Insert default admin user (password: admin123)
INSERT INTO task_user (username, password, email, is_active) 
VALUES ('admin', '$2a$10$X/uMNuiis.fyO47cxbta.OC8YOfPCBPT4rYLKJveq7rIqMacDqGOe', 'admin@example.com', TRUE)
ON DUPLICATE KEY UPDATE username=username;

-- Workflow instance table
CREATE TABLE IF NOT EXISTS task_workflow_instance (
    id INT AUTO_INCREMENT PRIMARY KEY,
    workflow_id INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    rtn_msg TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);


-- Add retry_interval_multiplier column to task_config table
ALTER TABLE task_config 
ADD COLUMN retry_interval_multiplier FLOAT DEFAULT 1.0 
COMMENT 'Multiplier for retry interval (for exponential backoff)';

-- Update existing records to have default value
UPDATE task_config 
SET retry_interval_multiplier = 1.0 
WHERE retry_interval_multiplier IS NULL;
