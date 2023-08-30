package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_user_group")
public class MUserGroupEntity extends BaseEntity{

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

    /**
    *  
    */
    @Column(name = "user_group_desc")
    private String userGroupDesc;

    /**
    *  
    */
    @Column(name = "user_group_name")
    private String userGroupName;


}