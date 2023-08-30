package com.abluepoint.summer.data.jpa.test.data.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_user_sms")
public class CUserSmsEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "c_user_id")
    private Long cUserId;

    /**
    *  
    */
    @Column(name = "cif_id")
    private Long cifId;

    /**
    *  
    */
    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    /**
    *  
    */
    @Column(name = "mesage_type")
    private String mesageType;

    /**
    *  
    */
    @Column(name = "message")
    private String message;

    /**
    *  
    */
    @Column(name = "phone")
    private String phone;

    /**
    *  
    */
    @Column(name = "send_state")
    private Short sendState;

    /**
    *  
    */
    @Column(name = "send_time")
    private LocalDateTime sendTime;

    /**
    *  
    */
    @Column(name = "state")
    private Byte state;

    /**
    *  
    */
    @Column(name = "verify_state")
    private Short verifyState;

    /**
    *  
    */
    @Column(name = "version")
    private Long version;


}