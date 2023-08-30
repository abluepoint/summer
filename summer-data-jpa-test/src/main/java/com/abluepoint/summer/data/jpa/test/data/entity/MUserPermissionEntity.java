package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_user_permission")
public class MUserPermissionEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "user_id")
    private Long userId;

    /**
    *  
    */
    @Column(name = "permission_id")
    private Long permissionId;


}