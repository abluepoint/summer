package com.abluepoint.summer.data.jpa.test.data.entity;


import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "hibernate_sequence")
public class HibernateSequenceEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "next_val")
    private Long nextVal;


}