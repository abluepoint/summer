package com.abluepoint.summer.sample.authorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Entity
@Table(name = "summer_data_scope_policy", uniqueConstraints = @UniqueConstraint(name = "uk_summer_data_scope_policy", columnNames = {"enterprise_id", "resource"}))
class DataScopePolicyEntity {
    @jakarta.persistence.Id @Column(name = "policy_id", nullable = false, length = 96) private String policyId;
    @Column(name = "enterprise_id", nullable = false, length = 64) private String enterpriseId;
    @Column(name = "resource", nullable = false, length = 160) private String resource;
    @Column(name = "operator_name", nullable = false, length = 8) private String operator;
    @Column(name = "rules_json", nullable = false, columnDefinition = "text") private String rulesJson;
    @Column(name = "conditions_json", nullable = false, columnDefinition = "text") private String conditionsJson;
    @Column(name = "checksum", nullable = false, length = 64) private String checksum;
    @Column(name = "version", nullable = false) private long version;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DataScopePolicyEntity() {}

    DataScopePolicyEntity(String id, String enterpriseId, String resource, String operator, String rulesJson) {
        this(id, enterpriseId, resource, operator, rulesJson, "null", "");
    }

    DataScopePolicyEntity(String id, String enterpriseId, String resource, String operator,
                          String rulesJson, String conditionsJson, String checksum) {
        this.policyId = id;
        this.enterpriseId = enterpriseId;
        this.resource = resource;
        this.operator = operator;
        this.rulesJson = rulesJson;
        this.conditionsJson = conditionsJson == null ? "null" : conditionsJson;
        this.checksum = checksum == null || checksum.isBlank() ? defaultChecksum(operator, this.rulesJson, this.conditionsJson) : checksum;
        this.version = 0;
        this.updatedAt = Instant.now();
    }

    void update(String operator, String rulesJson, String conditionsJson, String checksum) {
        this.operator = operator;
        this.rulesJson = rulesJson;
        this.conditionsJson = conditionsJson == null ? "null" : conditionsJson;
        this.checksum = checksum;
        this.version++;
        this.updatedAt = Instant.now();
    }

    private static String defaultChecksum(String operator, String rules, String conditions) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((operator + "|" + rules + "|" + conditions).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("checksum unavailable", exception);
        }
    }

    String policyId() { return policyId; }
    String enterpriseId() { return enterpriseId; }
    String resource() { return resource; }
    String operator() { return operator; }
    String rulesJson() { return rulesJson; }
    String conditionsJson() { return conditionsJson; }
    String checksum() { return checksum; }
    long version() { return version; }
    Instant updatedAt() { return updatedAt; }
}
