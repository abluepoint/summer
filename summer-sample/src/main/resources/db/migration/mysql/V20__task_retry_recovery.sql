ALTER TABLE summer_task_definition
    ADD COLUMN timeout_ms BIGINT NOT NULL DEFAULT 30000;

ALTER TABLE summer_task_execution
    ADD COLUMN idempotency_key VARCHAR(255) NULL,
    ADD COLUMN node_id VARCHAR(120) NULL,
    ADD COLUMN lease_until TIMESTAMP(6) NULL,
    ADD COLUMN recovery_count INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uk_summer_task_execution_key
    ON summer_task_execution (task_id, idempotency_key);

CREATE TABLE summer_task_execution_attempt (
  attempt_id VARCHAR(36) NOT NULL,
  execution_id VARCHAR(36) NOT NULL,
  attempt INT NOT NULL,
  status VARCHAR(16) NOT NULL,
  started_at TIMESTAMP(6) NOT NULL,
  finished_at TIMESTAMP(6) NULL,
  failure_code VARCHAR(120) NULL,
  node_id VARCHAR(120) NULL,
  CONSTRAINT pk_summer_task_execution_attempt PRIMARY KEY (attempt_id),
  CONSTRAINT uk_summer_task_attempt_number UNIQUE (execution_id, attempt),
  CONSTRAINT fk_summer_task_attempt_execution FOREIGN KEY (execution_id) REFERENCES summer_task_execution (execution_id),
  CONSTRAINT ck_summer_task_attempt_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED'))
) ENGINE=InnoDB;

CREATE INDEX ix_summer_task_attempt_execution ON summer_task_execution_attempt (execution_id, attempt);

CREATE TABLE summer_task_trigger_receipt (
  receipt_id VARCHAR(36) NOT NULL,
  enterprise_id VARCHAR(64) NOT NULL,
  task_id VARCHAR(36) NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  fingerprint VARCHAR(128) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  CONSTRAINT pk_summer_task_trigger_receipt PRIMARY KEY (receipt_id),
  CONSTRAINT uk_summer_task_trigger_key UNIQUE (enterprise_id, idempotency_key),
  CONSTRAINT fk_summer_task_trigger_task FOREIGN KEY (task_id) REFERENCES summer_task_definition (task_id)
) ENGINE=InnoDB;
