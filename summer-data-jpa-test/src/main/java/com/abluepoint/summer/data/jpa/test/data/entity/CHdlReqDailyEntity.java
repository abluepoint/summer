package com.abluepoint.summer.data.jpa.test.data.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_hdl_req_daily")
public class CHdlReqDailyEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "error_count")
    private Long errorCount;

    /**
    *  
    */
    @Column(name = "request_count")
    private Long requestCount;

    /**
    *  
    */
    @Column(name = "stat_date")
    private LocalDateTime statDate;

    /**
    *  
    */
    @Column(name = "success_count")
    private Long successCount;

    /**
    *  
    */
    @Column(name = "sys_id")
    private Long sysId;


}