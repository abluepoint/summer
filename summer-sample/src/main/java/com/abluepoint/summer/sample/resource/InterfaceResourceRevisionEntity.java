package com.abluepoint.summer.sample.resource;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable observation of the complete active endpoint set, not a permission assignment. */
@Entity
@Table(name = "summer_interface_resource_revision", uniqueConstraints =
        @UniqueConstraint(name = "uk_interface_resource_revision_fingerprint", columnNames = "fingerprint"))
class InterfaceResourceRevisionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revision") private Long revision;
    @Column(nullable = false, length = 64) private String fingerprint;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(name = "snapshot_json", nullable = false) private String snapshotJson;
    @Column(name = "resource_count", nullable = false) private int resourceCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected InterfaceResourceRevisionEntity() {}

    InterfaceResourceRevisionEntity(String fingerprint, String snapshotJson, int resourceCount) {
        this.fingerprint = fingerprint;
        this.snapshotJson = snapshotJson;
        this.resourceCount = resourceCount;
        this.createdAt = Instant.now();
    }

    Long getRevision() { return revision; }
    String getSnapshotJson() { return snapshotJson; }
    InterfaceResourceRevisionSummary summary() {
        return new InterfaceResourceRevisionSummary(revision, fingerprint, resourceCount, createdAt.toString());
    }
}
