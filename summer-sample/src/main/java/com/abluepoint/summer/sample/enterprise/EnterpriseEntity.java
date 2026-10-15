package com.abluepoint.summer.sample.enterprise;

import com.abluepoint.summer.data.jpa.entity.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_enterprise", uniqueConstraints = @UniqueConstraint(name = "uk_summer_enterprise_code", columnNames = "code"))
class EnterpriseEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64)
    private String enterpriseId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 64)
    private String code;
    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(nullable = false, length = 32)
    @org.hibernate.annotations.ColumnDefault("'PENDING'")
    private String reviewStatus = "PENDING";
    @org.hibernate.annotations.ColumnDefault("0")
    @jakarta.persistence.Version private long version;
    String reviewStatus() { return reviewStatus; }
    long version() { return version; }
    void status(String status) { this.status = status; }
    void reviewStatus(String status) { this.reviewStatus = status; }
    protected EnterpriseEntity() {}
    EnterpriseEntity(String enterpriseId, String name, String code) {
        this.enterpriseId = enterpriseId;
        this.name = name;
        this.code = code;
    }
    String enterpriseId() { return enterpriseId; }
    String name() { return name; }
    String code() { return code; }
    String status() { return status; }
}
