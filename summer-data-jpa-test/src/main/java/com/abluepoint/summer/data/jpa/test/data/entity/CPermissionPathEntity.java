package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_permission_path")
public class CPermissionPathEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "method")
    private String method;

    /**
    *  
    */
    @Column(name = "path")
    private String path;

    /**
    *  
    */
    @Column(name = "permission_id")
    private Long permissionId;

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