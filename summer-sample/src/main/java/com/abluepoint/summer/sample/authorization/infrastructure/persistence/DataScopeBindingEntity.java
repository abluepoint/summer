package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "summer_data_scope_binding", uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "resource", "subject_type", "subject_id"}))
public class DataScopeBindingEntity {
    @Id @Column(name="binding_id", length=96) String id;
    @Column(name="enterprise_id", nullable=false, length=64) String enterpriseId;
    @Column(nullable=false, length=160) String resource;
    @Column(name="subject_type", nullable=false, length=8) String subjectType;
    @Column(name="subject_id", nullable=false, length=128) String subjectId;
    @Column(name="operator_name", nullable=false, length=8) String operator;
    @Column(name="rules_json", nullable=false, columnDefinition="text") String rulesJson;
    @Column(name="conditions_json", nullable=false, columnDefinition="text") String conditionsJson;
    @Column(nullable=false, length=64) String checksum;
    @Version long version;
    @Column(name="updated_at", nullable=false) Instant updatedAt;
    public DataScopeBindingEntity() {}
    public void update(String enterprise, String resource, String kind, String subject, String operator,
                       String rules, String conditions, String checksum) {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        this.enterpriseId = enterprise; this.resource = resource; this.subjectType = kind; this.subjectId = subject;
        this.operator = operator; this.rulesJson = rules; this.conditionsJson = conditions;
        this.checksum = checksum; this.updatedAt = Instant.now();
    }
    public String id() { return id; }
    public String enterpriseId() { return enterpriseId; }
    public String resource() { return resource; }
    public String subjectType() { return subjectType; }
    public String subjectId() { return subjectId; }
    public String operator() { return operator; }
    public String rulesJson() { return rulesJson; }
    public String conditionsJson() { return conditionsJson; }
    public String checksum() { return checksum; }
    public long version() { return version; }
    public Instant updatedAt() { return updatedAt; }
}
