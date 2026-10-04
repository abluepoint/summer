package com.abluepoint.summer.sample.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "summer_dictionary_type")
class DictionaryTypeEntity {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;
    @Column(length = 80, nullable = false, unique = true)
    private String code;
    @Column(length = 200, nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean published;
    @Column(name = "public_readable", nullable = false)
    private boolean publicReadable;
    @Column(nullable = false)
    private boolean enabled;

    protected DictionaryTypeEntity() {}

    DictionaryTypeEntity(String id, String code, String name, boolean published, boolean publicReadable, boolean enabled) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.published = published;
        this.publicReadable = publicReadable;
        this.enabled = enabled;
    }

    String code() { return code; }
    String name() { return name; }
    boolean published() { return published; }
    boolean publicReadable() { return publicReadable; }
    boolean enabled() { return enabled; }
    void change(String name, boolean published, boolean publicReadable, boolean enabled) {
        this.name = name;
        this.published = published;
        this.publicReadable = publicReadable;
        this.enabled = enabled;
    }
}
