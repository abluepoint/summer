package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_user_group_user")
public class CUserGroupUserEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "user_id")
    private Long userId;

    /**
    *  
    */
    @Column(name = "user_group_id")
    private Long userGroupId;


}