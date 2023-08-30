package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "sys_sequence")
public class SysSequenceEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "seq_name")
    private String seqName;

    /**
    *  
    */
    @Column(name = "seq_value")
    private Integer seqValue;


}