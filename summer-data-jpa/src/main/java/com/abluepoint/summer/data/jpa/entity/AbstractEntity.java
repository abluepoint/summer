/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:AbstractEntity.java
 * Date:2020-09-10 13:53:10
 */

package com.abluepoint.summer.data.jpa.entity;


import javax.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.LocalDateTime;

@MappedSuperclass
public abstract class AbstractEntity {

    private LocalDateTime createTime;
    private Long createdBy;
    private LocalDateTime updateTime;
    private Long updatedBy;


    public LocalDateTime getCreateTime() {
        return this.createTime;
    }

    public Long getCreatedBy() {
        return this.createdBy;
    }

    public LocalDateTime getUpdateTime() {
        return this.updateTime;
    }

    public Long getUpdatedBy() {
        return this.updatedBy;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }
}
