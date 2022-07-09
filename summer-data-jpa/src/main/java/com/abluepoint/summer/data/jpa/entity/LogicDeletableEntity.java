package com.abluepoint.summer.data.jpa.entity;

import javax.persistence.MappedSuperclass;

@MappedSuperclass
public class LogicDeletableEntity extends AbstractEntity implements LogicDeletable{
    private Integer isDeleted;

    public Integer getIsDeleted() {
        return this.isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
