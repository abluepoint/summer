package com.abluepoint.summer.sample.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "summer_plugin")
class PluginEntity {
    @Id @Column(name = "plugin_id", length = 120, nullable = false) private String pluginId;
    @Column(name = "version", length = 40, nullable = false) private String version;
    @Column(name = "artifact_digest", length = 180, nullable = false) private String artifactDigest;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 24, nullable = false) private PluginStatus status;
    @Column(name = "trusted_flag", nullable = false) private boolean trusted;
    @Column(name = "diagnostic", length = 500, nullable = false) private String diagnostic;
    @Column(name = "revision", nullable = false) private long revision;
    @Column(name = "registered_by", length = 200) private String registeredBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PluginEntity() {}

    PluginEntity(String pluginId, String version, String artifactDigest, PluginStatus status,
                 boolean trusted, String diagnostic, String registeredBy) {
        this.pluginId = pluginId; this.version = version; this.artifactDigest = artifactDigest;
        this.status = status; this.trusted = trusted; this.diagnostic = diagnostic;
        this.registeredBy = registeredBy; this.revision = 1; this.createdAt = Instant.now(); this.updatedAt = createdAt;
    }

    void update(String version, String artifactDigest, PluginStatus status, boolean trusted, String diagnostic, String registeredBy) {
        this.version = version; this.artifactDigest = artifactDigest; this.status = status; this.trusted = trusted;
        this.diagnostic = diagnostic; this.registeredBy = registeredBy; this.revision++; this.updatedAt = Instant.now();
    }
    void setStatus(PluginStatus status, String diagnostic) { this.status = status; this.diagnostic = diagnostic; this.revision++; this.updatedAt = Instant.now(); }
    String pluginId() { return pluginId; } String version() { return version; } String artifactDigest() { return artifactDigest; }
    PluginStatus status() { return status; } boolean trusted() { return trusted; } String diagnostic() { return diagnostic; }
    long revision() { return revision; }
}
