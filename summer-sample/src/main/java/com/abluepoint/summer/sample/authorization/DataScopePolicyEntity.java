package com.abluepoint.summer.sample.authorization;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "summer_data_scope_policy", uniqueConstraints = @UniqueConstraint(name = "uk_summer_data_scope_policy", columnNames = {"enterprise_id", "resource"}))
class DataScopePolicyEntity {
    @Id @Column(name="policy_id", nullable=false, length=96) private String policyId;
    @Column(name="enterprise_id", nullable=false, length=64) private String enterpriseId;
    @Column(name="resource", nullable=false, length=160) private String resource;
    @Column(name="operator_name", nullable=false, length=8) private String operator;
    @Column(name="rules_json", nullable=false, columnDefinition="text") private String rulesJson;
    @Column(name="version", nullable=false) private long version;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    protected DataScopePolicyEntity() {}
    DataScopePolicyEntity(String id, String enterpriseId, String resource, String operator, String rulesJson) { this.policyId=id; this.enterpriseId=enterpriseId; this.resource=resource; this.operator=operator; this.rulesJson=rulesJson; this.version=0; this.updatedAt=Instant.now(); }
    void update(String operator, String rulesJson) { this.operator=operator; this.rulesJson=rulesJson; this.version++; this.updatedAt=Instant.now(); }
    String policyId(){return policyId;} String enterpriseId(){return enterpriseId;} String resource(){return resource;} String operator(){return operator;} String rulesJson(){return rulesJson;} long version(){return version;} Instant updatedAt(){return updatedAt;}
}
