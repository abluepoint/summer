package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_dict_group")
public class SysDictGroupEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "dict_id")
    private String dictId;

    /**
    *  
    */
    @Column(name = "name")
    private String name;

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