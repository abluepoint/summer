package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_cif")
public class CCifEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "cif_name")
    private String cifName;

    /**
    *  
    */
    @Column(name = "cif_no")
    private String cifNo;

    /**
    *  
    */
    @Column(name = "cif_type")
    private Integer cifType;

    /**
    *  
    */
    @Column(name = "state")
    private Byte state;

    /**
    *  
    */
    @Column(name = "sys_id")
    private Long sysId;


}