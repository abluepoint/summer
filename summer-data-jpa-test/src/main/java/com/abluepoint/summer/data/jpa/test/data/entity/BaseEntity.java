package com.abluepoint.summer.data.jpa.test.data.entity;

import cn.teleinfo.summer.data.jpa.entity.LogicDeletable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public class BaseEntity implements LogicDeletable, Serializable {


    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "create_time")
    @CreatedDate
    private LocalDateTime createTime;

    @Column(name = "created_by")
    @CreatedBy
    private Long createdBy;

    @Column(name = "pdate_time")
    @LastModifiedDate
    private LocalDateTime updateTime;

    @Column(name = "updated_by")
    @LastModifiedBy
    private Long updatedBy;

    @Column(name = "is_deleted")
    private Byte isDeleted = 0;

}