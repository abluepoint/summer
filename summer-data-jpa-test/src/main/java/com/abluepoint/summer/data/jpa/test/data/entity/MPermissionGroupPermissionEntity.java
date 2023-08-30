package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_permission_group_permission")
public class MPermissionGroupPermissionEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "permission_id")
    private Long permissionId;

    /**
    *  
    */
    @Column(name = "permission_group_id")
    private Long permissionGroupId;


}