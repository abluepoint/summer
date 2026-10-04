CREATE TABLE summer_task_progress_event (
  event_id BIGINT NOT NULL AUTO_INCREMENT,
  enterprise_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(36) NOT NULL,
  execution_id VARCHAR(36) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_json TEXT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_summer_task_progress_event PRIMARY KEY (event_id),
  CONSTRAINT fk_summer_task_progress_task FOREIGN KEY (task_id) REFERENCES summer_task_definition (task_id),
  CONSTRAINT fk_summer_task_progress_execution FOREIGN KEY (execution_id) REFERENCES summer_task_execution (execution_id)
) ENGINE=InnoDB;
CREATE INDEX ix_summer_task_progress_task_event
  ON summer_task_progress_event (enterprise_id, task_id, event_id);
