package com.abluepoint.summer.sample.enterprise.infrastructure;

import com.abluepoint.summer.data.jpa.persistence.api.BaseLongEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "summer_directory_journal", uniqueConstraints = @UniqueConstraint(name = "uk_summer_directory_operation", columnNames = {"enterprise_id", "namespace", "operation_key"}))
public class DirectoryJournalEntity extends BaseLongEntity {
    @Column(nullable = false, length = 64) String enterpriseId;
    @Column(nullable = false, length = 64) String namespace;
    @Column(nullable = false, length = 255) String operationKey;
    @Column(length = 128) String resourceId;
    @Column(columnDefinition = "TEXT") String fingerprint;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(nullable = false) String payload;
    protected DirectoryJournalEntity() {}
    DirectoryJournalEntity(String tenant, String namespace, String key, String resourceId, String fingerprint, String payload) {
        this.enterpriseId = tenant; this.namespace = namespace; this.operationKey = key;
        this.resourceId = resourceId; this.fingerprint = fingerprint; this.payload = payload;
    }
}
