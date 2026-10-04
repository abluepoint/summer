package com.abluepoint.summer.sample.systemparameter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "summer_system_parameter")
class SystemParameterEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;
    @Column(name = "tenant_id", nullable = false, updatable = false, length = 80)
    private String tenantId;
    @Column(name = "organization_id", length = 64) private String organizationId;
    @Column(name = "created_by", length = 128) private String createdBy;
    @Column(name = "owner_id", length = 128) private String ownerId;
    @Column(name = "parameter_key", nullable = false, unique = true, length = 120)
    private String key;
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SystemParameterType type;
    @Column(name = "value_json", columnDefinition = "text")
    private String valueJson;
    @Column(name = "required_flag", nullable = false)
    private boolean required;
    @Column(name = "min_value", precision = 38, scale = 10)
    private BigDecimal min;
    @Column(name = "max_value", precision = 38, scale = 10)
    private BigDecimal max;
    @Column(name = "value_pattern", length = 500)
    private String pattern;
    @Column(name = "public_flag", nullable = false)
    private boolean publicValue;
    @Column(name = "description", length = 500)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SystemParameterStatus status;
    @Version
    @Column(name = "version", nullable = false)
    private long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SystemParameterEntity() {
    }

    SystemParameterEntity(String key, SystemParameterType type, String valueJson, boolean required,
                           BigDecimal min, BigDecimal max, String pattern, boolean publicValue,
                           String description, String organizationId, String createdBy, String ownerId) {
        this.id = UuidV7.next();
        this.tenantId = "ent-acme";
        this.organizationId = organizationId; this.createdBy = createdBy; this.ownerId = ownerId;
        this.key = key;
        this.type = type;
        this.valueJson = valueJson;
        this.required = required;
        this.min = min;
        this.max = max;
        this.pattern = pattern;
        this.publicValue = publicValue;
        this.description = description;
        this.status = SystemParameterStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    void update(String valueJson, boolean required, BigDecimal min, BigDecimal max, String pattern,
                boolean publicValue, String description, SystemParameterStatus status) {
        this.valueJson = valueJson;
        this.required = required;
        this.min = min;
        this.max = max;
        this.pattern = pattern;
        this.publicValue = publicValue;
        this.description = description;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    String id() { return id; }
    String tenantId() { return tenantId; }
    String organizationId() { return organizationId; }
    String createdBy() { return createdBy; }
    String ownerId() { return ownerId; }
    String key() { return key; }
    SystemParameterType type() { return type; }
    String valueJson() { return valueJson; }
    boolean required() { return required; }
    BigDecimal min() { return min; }
    BigDecimal max() { return max; }
    String pattern() { return pattern; }
    boolean publicValue() { return publicValue; }
    String description() { return description; }
    SystemParameterStatus status() { return status; }
    long version() { return version; }
    Instant createdAt() { return createdAt; }
    Instant updatedAt() { return updatedAt; }
}
