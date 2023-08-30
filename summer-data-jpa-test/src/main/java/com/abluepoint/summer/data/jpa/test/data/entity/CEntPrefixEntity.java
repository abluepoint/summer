package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_ent_prefix")
public class CEntPrefixEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "cif_id")
    private Long cifId;

    /**
    *  
    */
    @Column(name = "disable_reason")
    private String disableReason;

    /**
    *  
    */
    @Column(name = "prefix")
    private String prefix;

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