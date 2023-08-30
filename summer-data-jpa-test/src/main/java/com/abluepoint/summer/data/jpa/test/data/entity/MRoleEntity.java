package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_role")
public class MRoleEntity extends BaseEntity{

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
    private String state;

    /**
    *  
    */
    @Column(name = "sys_id")
    private Long sysId;


}