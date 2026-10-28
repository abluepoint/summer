package com.abluepoint.summer.sample.configuration.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="summer_configuration_override", uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","resource","config_key","scope_id"}))
public class ConfigurationOverrideEntity {
    @Id @Column(length=36) public String id;
    @Column(name="tenant_id", nullable=false, length=80) public String tenantId;
    @Column(nullable=false,length=160) public String resource;
    @Column(name="config_key", nullable=false,length=160) public String key;
    @Column(name="scope_id", nullable=false,length=64) public String scopeId;
    @Column(name="organization_id",length=64) public String organizationId;
    @Column(name="created_by",nullable=false,length=128) public String createdBy;
    @Column(name="owner_id",nullable=false,length=128) public String ownerId;
    @Column(name="value_json",nullable=false,columnDefinition="text") public String valueJson;
    @Column(name="public_readable",nullable=false) public boolean publicReadable;
    @Column(nullable=false) public boolean enabled;
    @Version public long version;
    @Column(name="latest_revision",nullable=false) public long latestRevision;
    @Column(name="published_revision") public Long publishedRevision;
    @Column(name="updated_at",nullable=false) public Instant updatedAt;
    public ConfigurationOverrideEntity() {}
}
