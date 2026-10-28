package com.abluepoint.summer.data.jpa.persistence.api;

public interface LogicDeletable {

    boolean isDeleted();

    void setDeleted(boolean deleted);
}
