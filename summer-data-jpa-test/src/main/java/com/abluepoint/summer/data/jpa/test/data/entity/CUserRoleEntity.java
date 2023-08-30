package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_user_role")
public class CUserRoleEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "user_id")
    private Long userId;

    /**
    *  
    */
    @Column(name = "role_id")
    private Long roleId;


}