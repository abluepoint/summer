//package com.abluepoint.summer.common.dict;
//
//import lombok.Builder;
//import org.springframework.core.Ordered;
//
///*
// * Copyright (c) 2020 abluepoint All Rights Reserved.
// * File:Dict.java
// * Date:2020-03-20 16:10:20
// */
//
///**
// * id   name      code       parentId   desc
// * 0    dict      idType     null       字典
// * 1    dict      orgType    null
// * 2    dict      trade      null       行业
// * 3    dict      industry   2          二级行业
// * 4    idType    01         0          身份证
// * 5    trade     01         2
// * 6    trade     02         3
// * 7    industry  01         5
// * 8    industry  02         5
// */
//@Builder
//public class Dict implements Ordered {
//
//    private Long id;
//    private String code;
//    private Long parentId;
//    private String desc;
//    private int order;
//    private boolean disabled;
//    private String state;
//
//    private String path;
//
//    public Dict(){
//
//    }
//
//    public Dict(Long id, String code, Long parentId, String desc, Integer order, boolean disabled, String state, String path) {
//        this.id = id;
//        this.code = code;
//        this.parentId = parentId;
//        this.desc = desc;
//        this.order = order;
//        this.disabled = disabled;
//        this.state = state;
//        this.path = path;
//    }
//
//
//    public Long getId() {
//        return id;
//    }
//
//    public void setId(Long id) {
//        this.id = id;
//    }
//
//    public String getCode() {
//        return code;
//    }
//
//    public void setCode(String code) {
//        this.code = code;
//    }
//
//    public Long getParentId() {
//        return parentId;
//    }
//
//    public void setParentId(Long parentId) {
//        this.parentId = parentId;
//    }
//
//    public String getDesc() {
//        return desc;
//    }
//
//    public void setDesc(String desc) {
//        this.desc = desc;
//    }
//
//    @Override
//    public int getOrder() {
//        return order;
//    }
//
//    public void setOrder(Integer order) {
//        this.order = order;
//    }
//
//    public boolean isDisabled() {
//        return disabled;
//    }
//
//    public void setDisabled(boolean disabled) {
//        this.disabled = disabled;
//    }
//
//    public String getState() {
//        return state;
//    }
//
//    public void setState(String state) {
//        this.state = state;
//    }
//
//    public String getPath() {
//        return path;
//    }
//
//    public void setPath(String path) {
//        this.path = path;
//    }
//}
