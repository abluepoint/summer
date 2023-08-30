package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_user_group_role")
public class CUserGroupRoleEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "user_group_id")
    private Integer userGroupId;

    /**
    *  
    */
    @Column(name = "role_id")
    private Integer roleId;


}