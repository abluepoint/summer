package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_permission")
public class CPermissionEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "perm_code")
    private String permCode;

    /**
    *  
    */
    @Column(name = "perm_desc")
    private String permDesc;

    /**
    *  
    */
    @Column(name = "perm_name")
    private String permName;

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