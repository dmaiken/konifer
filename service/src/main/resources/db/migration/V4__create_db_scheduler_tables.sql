CREATE TABLE IF NOT EXISTS scheduled_tasks (
     task_name TEXT NOT NULL,
     task_instance TEXT NOT NULL,
     task_data bytea,
     execution_time TIMESTAMP WITH TIME ZONE NOT NULL,
     picked BOOLEAN NOT NULL,
     picked_by TEXT,
     last_success TIMESTAMP WITH TIME ZONE,
     last_failure TIMESTAMP WITH TIME ZONE,
     consecutive_failures INT,
     last_heartbeat TIMESTAMP WITH TIME ZONE,
     version BIGINT NOT NULL,
     priority SMALLINT,
     PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX IF NOT EXISTS execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX IF NOT EXISTS last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX IF NOT EXISTS priority_execution_time_idx ON scheduled_tasks (priority DESC, execution_time ASC);

-- an optimization for users of priority might be to add priority to the execution_time_idx
-- this _might_ save reads as the priority-value is already in the index
-- CREATE INDEX IF NOT EXISTS execution_time_idx ON scheduled_tasks (execution_time asc, priority desc);
