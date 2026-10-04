package com.abluepoint.summer.sample.systemparameter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

enum SystemParameterReleaseState {
    DRAFT, PUBLISHED, ROLLED_BACK, INACTIVE
}

final class SystemParameterDraftRequest {
    private Object value;
    private Boolean required;
    private BigDecimal min;
    private BigDecimal max;
    private String pattern;
    private Boolean publicValue;
    private String description;
    private Long baseVersion;
    private String idempotencyKey;

    public Object value() { return value; }
    public Boolean required() { return required; }
    public BigDecimal min() { return min; }
    public BigDecimal max() { return max; }
    public String pattern() { return pattern; }
    public Boolean publicValue() { return publicValue; }
    public String description() { return description; }
    public Long baseVersion() { return baseVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public void setValue(Object value) { this.value = value; }
    public void setRequired(Boolean required) { this.required = required; }
    public void setMin(BigDecimal min) { this.min = min; }
    public void setMax(BigDecimal max) { this.max = max; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public void setPublicValue(Boolean publicValue) { this.publicValue = publicValue; }
    public void setPublic(Boolean publicValue) { this.publicValue = publicValue; }
    public void setDescription(String description) { this.description = description; }
    public void setBaseVersion(Long baseVersion) { this.baseVersion = baseVersion; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}

final class SystemParameterReleaseCommand {
    private Long revision;
    private Long expectedVersion;
    private String idempotencyKey;

    public Long revision() { return revision; }
    public Long expectedVersion() { return expectedVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public void setRevision(Long revision) { this.revision = revision; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}

record SystemParameterSnapshot(
        SystemParameterType type, Object value, boolean required, BigDecimal min, BigDecimal max,
        String pattern, boolean publicValue, String description, SystemParameterStatus status) {}

record SystemParameterVersionView(
        long revision, SystemParameterReleaseState state, String parameterKey, long baseVersion,
        String principal, Instant createdAt, Object value, SystemParameterStatus status,
        long currentVersion) {}

record SystemParameterVersionPage(
        List<SystemParameterVersionView> items, long currentVersion, SystemParameterReleaseState currentState) {}

record SystemParameterReleaseResult(
        SystemParameterView parameter, long revision, SystemParameterReleaseState state) {}
