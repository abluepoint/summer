package com.abluepoint.summer.sample.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "summer_dictionary_item", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summer_dictionary_item_value", columnNames = {"dictionary_code", "item_value"})
})
class DictionaryItemEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(name = "dictionary_code", length = 80, nullable = false, updatable = false)
    private String dictionaryCode;
    @Column(name = "item_value", length = 80, nullable = false)
    private String value;
    @Column(length = 200, nullable = false)
    private String label;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(nullable = false)
    private boolean enabled;

    protected DictionaryItemEntity() {}

    DictionaryItemEntity(String id, String dictionaryCode, String value, String label, int sortOrder, boolean enabled) {
        this.id = id;
        this.dictionaryCode = dictionaryCode;
        this.value = value;
        this.label = label;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }

    String id() { return id; }
    String dictionaryCode() { return dictionaryCode; }
    String value() { return value; }
    String label() { return label; }
    int sortOrder() { return sortOrder; }
    boolean enabled() { return enabled; }
    void change(String label, int sortOrder, boolean enabled) {
        this.label = label;
        this.sortOrder = sortOrder;
        this.enabled = enabled;
    }
}
