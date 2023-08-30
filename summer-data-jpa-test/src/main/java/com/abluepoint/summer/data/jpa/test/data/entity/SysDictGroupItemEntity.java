package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_dict_group_item")
public class SysDictGroupItemEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "dict_item_id")
    private String dictItemId;

    /**
    *  
    */
    @Column(name = "group_disabled")
    private Integer groupDisabled;

    /**
    *  
    */
    @Column(name = "group_id")
    private String groupId;

    /**
    *  
    */
    @Column(name = "group_order")
    private Integer groupOrder;

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