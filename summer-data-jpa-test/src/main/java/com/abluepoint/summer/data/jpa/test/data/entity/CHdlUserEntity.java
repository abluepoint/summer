package com.abluepoint.summer.data.jpa.test.data.entity;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Getter
@Setter
@Entity
@Table(name = "c_hdl_user")
public class CHdlUserEntity extends BaseEntity{

    /**
    *  
    */
    @Column(name = "cif_id")
    private Long cifId;

    /**
    *  
    */
    @Column(name = "ent_prefix_id")
    private Long entPrefixId;

    /**
    *  
    */
    @Column(name = "is_prefix_admin")
    private Integer isPrefixAdmin;

    /**
    *  
    */
    @Column(name = "private_key")
    private String privateKey;

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
    @Column(name = "user_hdl")
    private String userHdl;

    /**
    *  
    */
    @Column(name = "user_index")
    private Integer userIndex;


}