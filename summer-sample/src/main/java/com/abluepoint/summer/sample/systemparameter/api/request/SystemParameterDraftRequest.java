package com.abluepoint.summer.sample.systemparameter.api.request;

import com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterDraftCommand;

import java.math.BigDecimal;

public final class SystemParameterDraftRequest {
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

    public com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterDraftCommand toCommand() { return new com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterDraftCommand(value,required,min,max,pattern,publicValue,description,baseVersion,idempotencyKey); }
}
