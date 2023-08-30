package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_role_permission")
public class MRolePermissionEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "role_id")
    private Long roleId;

    /**
    *  
    */
    @Column(name = "permission_id")
    private Long permissionId;


}