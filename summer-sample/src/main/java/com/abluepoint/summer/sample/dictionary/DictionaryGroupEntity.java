package com.abluepoint.summer.sample.dictionary;

import jakarta.persistence.*;

@Entity
@Table(name="summer_dictionary_group", uniqueConstraints={
    @UniqueConstraint(columnNames={"tenant_id","dictionary_code","code"}),
    @UniqueConstraint(columnNames={"tenant_id","dictionary_code","id"})})
class DictionaryGroupEntity {
    @Id @Column(length=36) String id;
    @Column(name="tenant_id",nullable=false,length=80,updatable=false) String tenantId;
    @Column(name="dictionary_code",nullable=false,length=80,updatable=false) String dictionaryCode;
    @Column(nullable=false,length=80) String code;
    @Column(nullable=false,length=200) String name;
    @Column(nullable=false) boolean enabled;
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(name="ever_published",nullable=false,updatable=false) boolean everPublished;
    protected DictionaryGroupEntity() {}
    DictionaryGroupEntity(String tenant, String dictionary, String id, String code, String name, boolean enabled) {
        this.tenantId=tenant; this.dictionaryCode=dictionary; this.id=id; this.code=code; this.name=name; this.enabled=enabled;
    }
}
