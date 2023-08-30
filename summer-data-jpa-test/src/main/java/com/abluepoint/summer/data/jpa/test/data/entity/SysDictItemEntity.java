package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_dict_item")
public class SysDictItemEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "dict_id")
    private String dictId;

    /**
    *  
    */
    @Column(name = "item_desc")
    private String itemDesc;

    /**
    *  
    */
    @Column(name = "item_key")
    private String itemKey;

    /**
    *  
    */
    @Column(name = "item_name")
    private String itemName;

    /**
    *  
    */
    @Column(name = "item_order")
    private Integer itemOrder;

    /**
    *  
    */
    @Column(name = "parent_id")
    private Long parentId;

    /**
    *  
    */
    @Column(name = "parent_item_key")
    private String parentItemKey;

    /**
    *  
    */
    @Column(name = "state")
    private Integer state;

    /**
    *  
    */
    @Column(name = "sys_id")
    private Long sysId;


}