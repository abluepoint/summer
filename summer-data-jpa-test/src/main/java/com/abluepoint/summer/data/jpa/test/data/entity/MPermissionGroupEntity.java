package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_permission_group")
public class MPermissionGroupEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "parent_id")
    private Long parentId;

    /**
    *  
    */
    @Column(name = "pg_name")
    private String pgName;

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