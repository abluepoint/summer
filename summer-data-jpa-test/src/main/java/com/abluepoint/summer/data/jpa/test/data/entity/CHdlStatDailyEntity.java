package com.abluepoint.summer.data.jpa.test.data.entity;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_hdl_stat_daily")
public class CHdlStatDailyEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "delete_count")
    private Long deleteCount;

    /**
    *  
    */
    @Column(name = "reg_count")
    private Long regCount;

    /**
    *  
    */
    @Column(name = "reg_db_count")
    private Long regDbCount;

    /**
    *  
    */
    @Column(name = "resolve_count")
    private Long resolveCount;

    /**
    *  
    */
    @Column(name = "stat_date")
    private LocalDate statDate;

    /**
    *  
    */
    @Column(name = "sys_id")
    private Long sysId;


}