package com.abluepoint.summer.sample.authorization;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "summer_data_scope_binding", uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "resource", "subject_type", "subject_id"}))
class DataScopeBindingEntity {
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
    protected DataScopeBindingEntity() {}
}
