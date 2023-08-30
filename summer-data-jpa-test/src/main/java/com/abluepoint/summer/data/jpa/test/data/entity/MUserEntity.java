package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_user")
public class MUserEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "email")
    private String email;

    /**
    *  
    */
    @Column(name = "password")
    private String password;

    /**
    *  
    */
    @Column(name = "phone")
    private String phone;

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
    @Column(name = "username")
    private String username;


}