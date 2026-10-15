-- Existing events remain unchanged; new events may carry an OpenTelemetry trace.
ALTER TABLE summer_audit_event ADD COLUMN trace_id VARCHAR(32) NULL;
CREATE INDEX ix_summer_audit_trace ON summer_audit_event(trace_id);
