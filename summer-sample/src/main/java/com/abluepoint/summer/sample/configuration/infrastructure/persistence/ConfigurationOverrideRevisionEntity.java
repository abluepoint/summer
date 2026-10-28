package com.abluepoint.summer.sample.configuration.infrastructure.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="summer_configuration_override_revision",uniqueConstraints=@UniqueConstraint(columnNames={"override_id","revision"}))
public class ConfigurationOverrideRevisionEntity {
    @Id @Column(length=36) public String id;
    @Column(name="override_id",nullable=false,length=36) public String overrideId;
    @Column(name="tenant_id",nullable=false,length=80) public String tenantId;
    @Column(nullable=false) public long revision;
    @Column(nullable=false,length=20) public String state;
    @Column(name="value_json",nullable=false,columnDefinition="text") public String valueJson;
    @Column(name="public_readable",nullable=false) public boolean publicReadable;
    @Column(nullable=false) public boolean enabled;
    @Column(nullable=false,length=128) public String principal;
    @Column(name="request_id",length=120) public String requestId;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    public ConfigurationOverrideRevisionEntity() {}
}
