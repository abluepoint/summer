package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_hdl_app")
public class CHdlAppEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "app_name")
    private String appName;

    /**
    *  
    */
    @Column(name = "app_no")
    private String appNo;

    /**
    *  
    */
    @Column(name = "cif_id")
    private Long cifId;

    /**
    *  
    */
    @Column(name = "description")
    private String description;

    /**
    *  
    */
    @Column(name = "hdl_user_id")
    private Long hdlUserId;

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


}