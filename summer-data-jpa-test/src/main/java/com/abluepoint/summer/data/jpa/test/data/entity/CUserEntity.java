package com.abluepoint.summer.data.jpa.test.data.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_user")
public class CUserEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "cif_id")
    private Long cifId;

    /**
    *  
    */
    @Column(name = "email")
    private String email;

    /**
    *  
    */
    @Column(name = "is_pwd_new")
    private Short isPwdNew;

    /**
    *  
    */
    @Column(name = "is_username_new")
    private Short isUsernameNew;

    /**
    *  
    */
    @Column(name = "nick_name")
    private String nickName;

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
    @Column(name = "pwd_update_time")
    private LocalDateTime pwdUpdateTime;

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
    @Column(name = "user_name")
    private String userName;


}