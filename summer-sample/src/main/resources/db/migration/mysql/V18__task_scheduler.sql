CREATE TABLE summer_task_definition (
  task_id VARCHAR(36) NOT NULL,
  enterprise_id VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  task_type VARCHAR(120) NOT NULL,
  cron_expression VARCHAR(120) NOT NULL,
  parameters_json TEXT NULL,
  max_retries INT NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_summer_task_definition PRIMARY KEY (task_id),
  CONSTRAINT uk_summer_task_definition_name UNIQUE (enterprise_id, name),
  CONSTRAINT ck_summer_task_definition_status CHECK (status IN ('ACTIVE', 'PAUSED')),
  CONSTRAINT ck_summer_task_definition_retries CHECK (max_retries >= 0 AND max_retries <= 10)
) ENGINE=InnoDB;
CREATE INDEX ix_summer_task_definition_enterprise_status ON summer_task_definition (enterprise_id, status, updated_at);

CREATE TABLE summer_task_execution (
  execution_id VARCHAR(36) NOT NULL,
  task_id VARCHAR(36) NOT NULL,
  enterprise_id VARCHAR(64) NOT NULL,
  fire_instance_id VARCHAR(200) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempt INT NOT NULL DEFAULT 1,
  started_at TIMESTAMP(6) NOT NULL,
  finished_at TIMESTAMP(6) NULL,
  failure_code VARCHAR(120) NULL,
  CONSTRAINT pk_summer_task_execution PRIMARY KEY (execution_id),
  CONSTRAINT uk_summer_task_execution_fire UNIQUE (task_id, fire_instance_id),
  CONSTRAINT fk_summer_task_execution_task FOREIGN KEY (task_id) REFERENCES summer_task_definition (task_id),
  CONSTRAINT ck_summer_task_execution_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB;
CREATE INDEX ix_summer_task_execution_task_time ON summer_task_execution (enterprise_id, task_id, started_at);
