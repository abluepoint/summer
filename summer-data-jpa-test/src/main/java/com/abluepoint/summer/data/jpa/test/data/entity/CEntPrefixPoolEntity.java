package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_ent_prefix_pool")
public class CEntPrefixPoolEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "state")
    private Integer state;


}