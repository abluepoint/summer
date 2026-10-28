package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "summer_dictionary_type", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"tenant_id", "code"}))
public class DictionaryTypeEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(length = 80, nullable = false)
    private String code;
    @Column(name="tenant_id", nullable=false, length=80) private String tenantId;
    @Column(name="organization_id", length=64) private String organizationId;
    @Column(name="created_by", length=128) private String createdBy;
    @Column(name="owner_id", length=128) private String ownerId;
    @Column(length = 200, nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean published;
    @Column(name = "public_readable", nullable = false)
    private boolean publicReadable;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "published_version")
    private Long publishedVersion;

    protected DictionaryTypeEntity() {}

    public DictionaryTypeEntity(String tenantId, String id, String code, String name, boolean published, boolean publicReadable, boolean enabled, String organizationId, String createdBy, String ownerId) {
        this.id = id;
        this.tenantId = tenantId; this.organizationId = organizationId; this.createdBy = createdBy; this.ownerId = ownerId;
        this.code = code;
        this.name = name;
        this.published = published;
        this.publicReadable = publicReadable;
        this.enabled = enabled;
    }

    public String tenantId(){return tenantId;} public String organizationId(){return organizationId;} public String createdBy(){return createdBy;} public String ownerId(){return ownerId;}
    public String code() { return code; }
    public String name() { return name; }
    public boolean published() { return published; }
    public boolean publicReadable() { return publicReadable; }
    public boolean enabled() { return enabled; }
    public Long publishedVersion() { return publishedVersion; }
    public void change(String name, boolean published, boolean publicReadable, boolean enabled) {
        this.name = name;
        this.published = published;
        this.publicReadable = publicReadable;
        this.enabled = enabled;
    }

    public void publish(long version) {
        this.published = true;
        this.publishedVersion = version;
    }

    public void disable() { this.published = false; }
}
