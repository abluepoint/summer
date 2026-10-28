package com.abluepoint.summer.sample.dictionary.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_dictionary_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summer_dictionary_item_value", columnNames = {"tenant_id", "dictionary_code", "item_value"})
})
public class DictionaryItemEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "dictionary_code", length = 80, nullable = false, updatable = false)
    private String dictionaryCode;
    @Column(name="tenant_id", nullable=false, length=80) private String tenantId;
    @Column(name="organization_id", length=64) private String organizationId;
    @Column(name="created_by", length=128) private String createdBy;
    @Column(name="owner_id", length=128) private String ownerId;
    @Column(name = "item_value", length = 80, nullable = false)
    private String value;
    @Column(length = 200, nullable = false)
    private String label;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean enabled;

    protected DictionaryItemEntity() {}

    public DictionaryItemEntity(String tenantId, String id, String dictionaryCode, String value, String label, int sortOrder, boolean enabled, String organizationId, String createdBy, String ownerId) {
        this.id = id;
        this.tenantId = tenantId; this.organizationId = organizationId; this.createdBy = createdBy; this.ownerId = ownerId;
        this.dictionaryCode = dictionaryCode;
        this.value = value;
        this.label = label;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    public String id() { return id; }
    public String tenantId(){return tenantId;} public String organizationId(){return organizationId;} public String createdBy(){return createdBy;} public String ownerId(){return ownerId;}
    public String dictionaryCode() { return dictionaryCode; }
    public String value() { return value; }
    public String label() { return label; }
    public int sortOrder() { return sortOrder; }
    public boolean enabled() { return enabled; }
    public void change(String label, int sortOrder, boolean enabled) {
        this.label = label;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }
}
