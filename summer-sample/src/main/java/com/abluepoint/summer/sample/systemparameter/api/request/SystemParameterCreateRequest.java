package com.abluepoint.summer.sample.systemparameter.api.request;

import com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterCreateCommand;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterType;

import java.math.BigDecimal;

public final class SystemParameterCreateRequest {
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

    public com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterCreateCommand toCommand() { return new com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterCreateCommand(key,type,value,required,min,max,pattern,publicValue,description,organizationId,createdBy,ownerId); }
}
