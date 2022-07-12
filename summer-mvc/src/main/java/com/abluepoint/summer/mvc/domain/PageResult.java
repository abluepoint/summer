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
    private Integer pageNumber;
    private long totalCount;
    private Integer totalPage;

    private List<T> content;

    public static <T> PageResult<T> of(Page<T> page) {
        PageResult<T> pageResult = new PageResult(page.getSize(),page.getNumber()+1,page.getTotalElements(),page.getTotalPages());
        pageResult.setContent(page.getContent());
        return pageResult;
    }

    public PageResult(){

    }

    public PageResult(Integer pageSize, Integer pageNo, long totalCount, Integer totalPage) {
        this.pageSize = pageSize;
        this.pageNumber = pageNo;
        this.totalCount = totalCount;
        this.totalPage = totalPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
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

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }
}
