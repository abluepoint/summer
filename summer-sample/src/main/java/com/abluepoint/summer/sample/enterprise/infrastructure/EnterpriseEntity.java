package com.abluepoint.summer.sample.enterprise.infrastructure;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_enterprise", uniqueConstraints = @UniqueConstraint(name = "uk_summer_enterprise_code", columnNames = "code"))
public class EnterpriseEntity extends BaseLongEntity {
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
    public String reviewStatus() { return reviewStatus; }
    public long version() { return version; }
    public void status(String status) { this.status = status; }
    public void reviewStatus(String status) { this.reviewStatus = status; }
    protected EnterpriseEntity() {}
    public EnterpriseEntity(String enterpriseId, String name, String code) {
        this.enterpriseId = enterpriseId;
        this.name = name;
        this.code = code;
    }
    public String enterpriseId() { return enterpriseId; }
    public String name() { return name; }
    public String code() { return code; }
    public String status() { return status; }
}
