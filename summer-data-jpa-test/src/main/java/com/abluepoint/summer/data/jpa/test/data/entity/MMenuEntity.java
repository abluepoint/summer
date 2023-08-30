package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "m_menu")
public class MMenuEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "code")
    private String code;

    /**
    *  
    */
    @Column(name = "component")
    private String component;

    /**
    *  
    */
    @Column(name = "hidden")
    private Integer hidden;

    /**
    *  
    */
    @Column(name = "icon")
    private String icon;

    /**
    *  
    */
    @Column(name = "name")
    private String name;

    /**
    *  
    */
    @Column(name = "parent_id")
    private Long parentId;

    /**
    *  
    */
    @Column(name = "path")
    private String path;

    /**
    *  
    */
    @Column(name = "permission")
    private String permission;

    /**
    *  
    */
    @Column(name = "sort")
    private Integer sort;


}