CREATE TABLE scheduled_tasks
(
    task_name            TEXT                     NOT NULL,
    task_instance        TEXT                     NOT NULL,
    task_data            BYTEA,
    execution_time       TIMESTAMP WITH TIME ZONE NOT NULL,
    picked               BOOLEAN                  NOT NULL,
    picked_by            TEXT,
    last_success         TIMESTAMP WITH TIME ZONE,
    last_failure         TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat       TIMESTAMP WITH TIME ZONE,
    version              BIGINT                   NOT NULL,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON scheduled_tasks (last_heartbeat);

DROP INDEX IF EXISTS idx_scheduled_tasks_history_task_name_timestamp;
DROP TABLE IF EXISTS scheduled_tasks_history;

create table scheduled_tasks_history
(
    id        UUID                     NOT NULL DEFAULT GEN_RANDOM_UUID(),
    ident     VARCHAR(50)              NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    task_name VARCHAR(100)             NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_scheduled_tasks_history_task_name_timestamp ON scheduled_tasks_history (task_name, timestamp);