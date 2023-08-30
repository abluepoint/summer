package com.abluepoint.summer.data.jpa.test.data.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_hdl_log")
public class CHdlLogEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "app_id")
    private Integer appId;

    /**
    *  
    */
    @Column(name = "app_name")
    private String appName;

    /**
    *  
    */
    @Column(name = "hdl")
    private String hdl;

    /**
    *  
    */
    @Column(name = "hdl_user_id")
    private String hdlUserId;

    /**
    *  
    */
    @Column(name = "op_code")
    private Integer opCode;

    /**
    *  
    */
    @Column(name = "prefix")
    private String prefix;

    /**
    *  
    */
    @Column(name = "request_data")
    private String requestData;

    /**
    *  
    */
    @Column(name = "request_time")
    private LocalDateTime requestTime;

    /**
    *  
    */
    @Column(name = "response_code")
    private Integer responseCode;

    /**
    *  
    */
    @Column(name = "response_data")
    private String responseData;

    /**
    *  
    */
    @Column(name = "response_desc")
    private String responseDesc;

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
    @Column(name = "use_time")
    private Long useTime;

    /**
    *  
    */
    @Column(name = "user_hdl")
    private String userHdl;

    /**
    *  
    */
    @Column(name = "user_index")
    private Integer userIndex;


}