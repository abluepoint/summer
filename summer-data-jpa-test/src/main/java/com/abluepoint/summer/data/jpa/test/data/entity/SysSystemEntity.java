package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_system")
public class SysSystemEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "default_flag")
    private Integer defaultFlag;

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


}