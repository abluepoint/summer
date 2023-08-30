package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_dict")
public class SysDictEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "dict_name")
    private String dictName;

    /**
    *  
    */
    @Column(name = "parent_id")
    private String parentId;

    /**
    *  
    */
    @Column(name = "state")
    private Short state;

    /**
    *  
    */
    @Column(name = "sys_id")
    private Long sysId;


}