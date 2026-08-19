package com.abluepoint.summer.data.jpa.entity;

public interface LogicDeletable {

    boolean isDeleted();

    void setDeleted(boolean deleted);
}
