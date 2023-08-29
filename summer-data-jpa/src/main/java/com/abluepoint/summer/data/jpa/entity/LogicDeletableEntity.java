package com.abluepoint.summer.data.jpa.entity;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class LogicDeletableEntity extends AbstractEntity implements LogicDeletable{
    private Byte isDeleted;

    public Byte getIsDeleted() {
        return this.isDeleted;
    }

    public void setIsDeleted(Byte isDeleted) {
        this.isDeleted = isDeleted;
    }
}
