package com.abluepoint.summer.sample.dictionary;

import jakarta.persistence.*;

@Entity
@Table(name="summer_dictionary_group_member",uniqueConstraints={
    @UniqueConstraint(columnNames={"tenant_id","dictionary_code","group_id","item_id"})})
class DictionaryGroupMemberEntity {
    @Id @Column(length=36) String id;
    @Column(name="tenant_id",nullable=false,length=80,updatable=false) String tenantId;
    @Column(name="dictionary_code",nullable=false,length=80,updatable=false) String dictionaryCode;
    @Column(name="group_id",nullable=false,length=36,updatable=false) String groupId;
    @Column(name="item_id",nullable=false,length=36,updatable=false) String itemId;
    @Column(name="sort_order",nullable=false) int sortOrder;
    protected DictionaryGroupMemberEntity() {}
    DictionaryGroupMemberEntity(String tenant, String dictionary, String group, String item, int order) {
        id=java.util.UUID.randomUUID().toString(); tenantId=tenant; dictionaryCode=dictionary; groupId=group; itemId=item; sortOrder=order;
    }
}
