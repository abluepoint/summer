package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_properties")
public class SysPropertiesEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "property_name")
    private String propertyName;

    /**
    *  
    */
    @Column(name = "property_type")
    private String propertyType;

    /**
    *  
    */
    @Column(name = "property_value")
    private String propertyValue;

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