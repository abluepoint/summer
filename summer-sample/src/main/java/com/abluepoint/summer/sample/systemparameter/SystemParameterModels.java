package com.abluepoint.summer.sample.systemparameter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

enum SystemParameterType {
    STRING, INTEGER, DECIMAL, BOOLEAN
}

enum SystemParameterStatus {
    ACTIVE, INACTIVE
}

final class CreateSystemParameterRequest {
    private String key;
    private SystemParameterType type;
    private Object value;
    private Boolean required;
    private BigDecimal min;
    private BigDecimal max;
    private String pattern;
    private Boolean publicValue;
    private String description;
    private String organizationId;
    private String createdBy;
    private String ownerId;

    public String key() { return key; }
    public SystemParameterType type() { return type; }
    public Object value() { return value; }
    public Boolean required() { return required; }
    public BigDecimal min() { return min; }
    public BigDecimal max() { return max; }
    public String pattern() { return pattern; }
    public Boolean publicValue() { return publicValue; }
    public String description() { return description; }
    public String organizationId() { return organizationId; }
    public String createdBy() { return createdBy; }
    public String ownerId() { return ownerId; }

    public void setKey(String key) { this.key = key; }
    public void setType(SystemParameterType type) { this.type = type; }
    public void setValue(Object value) { this.value = value; }
    public void setRequired(Boolean required) { this.required = required; }
    public void setMin(BigDecimal min) { this.min = min; }
    public void setMax(BigDecimal max) { this.max = max; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public void setPublicValue(Boolean publicValue) { this.publicValue = publicValue; }
    public void setPublic(Boolean publicValue) { this.publicValue = publicValue; }
    public void setDescription(String description) { this.description = description; }
    public void setOrganizationId(String value) { this.organizationId = value; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public void setOwnerId(String value) { this.ownerId = value; }
}

final class UpdateSystemParameterRequest {
    private Object value;
    private Boolean required;
    private BigDecimal min;
    private BigDecimal max;
    private String pattern;
    private Boolean publicValue;
    private String description;
    private SystemParameterStatus status;
    private Long version;

    public Object value() { return value; }
    public Boolean required() { return required; }
    public BigDecimal min() { return min; }
    public BigDecimal max() { return max; }
    public String pattern() { return pattern; }
    public Boolean publicValue() { return publicValue; }
    public String description() { return description; }
    public SystemParameterStatus status() { return status; }
    public Long version() { return version; }

    public void setValue(Object value) { this.value = value; }
    public void setRequired(Boolean required) { this.required = required; }
    public void setMin(BigDecimal min) { this.min = min; }
    public void setMax(BigDecimal max) { this.max = max; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public void setPublicValue(Boolean publicValue) { this.publicValue = publicValue; }
    public void setPublic(Boolean publicValue) { this.publicValue = publicValue; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(SystemParameterStatus status) { this.status = status; }
    public void setVersion(Long version) { this.version = version; }
}

record SystemParameterView(
        String id,
        String key,
        SystemParameterType type,
        Object value,
        boolean required,
        BigDecimal min,
        BigDecimal max,
        String pattern,
        boolean publicValue,
        String description,
        SystemParameterStatus status,
        long version,
        String tenantId,
        Instant createdAt,
        Instant updatedAt
) {
}

record SystemParameterPage(
        List<SystemParameterView> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
