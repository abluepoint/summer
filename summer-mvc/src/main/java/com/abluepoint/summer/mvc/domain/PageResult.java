/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:PageResult.java
 * Date:2020-04-02 22:15:02
 */

package com.abluepoint.summer.mvc.domain;

import org.springframework.data.domain.Page;

import java.util.List;

public class PageResult<T> {

    private Integer pageSize;
    private Integer pageNo;
    private long totalCount;
    private Integer totalPage;

    private List<T> data;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> pageResult = new PageResult(page.getSize(),page.getNumber()+1,page.getTotalElements(),page.getTotalPages());
        pageResult.setData(page.getContent());
        return pageResult;
    }

    public PageResult(){

    }

    public PageResult(Integer pageSize, Integer pageNo, long totalCount, Integer totalPage) {
        this.pageSize = pageSize;
        this.pageNo = pageNo;
        this.totalCount = totalCount;
        this.totalPage = totalPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(Integer totalPage) {
        this.totalPage = totalPage;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }
}
