-- Add retry_interval_multiplier column to task_config table
ALTER TABLE task_config 
ADD COLUMN retry_interval_multiplier FLOAT DEFAULT 1.0 
COMMENT 'Multiplier for retry interval (for exponential backoff)';

-- Update existing records to have default value
UPDATE task_config 
SET retry_interval_multiplier = 1.0 
WHERE retry_interval_multiplier IS NULL;
