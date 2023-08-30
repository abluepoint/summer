package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_role")
public class CRoleEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "role_code")
    private String roleCode;

    /**
    *  
    */
    @Column(name = "role_desc")
    private String roleDesc;

    /**
    *  
    */
    @Column(name = "role_name")
    private String roleName;

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