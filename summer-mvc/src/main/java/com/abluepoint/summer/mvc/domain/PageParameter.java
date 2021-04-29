/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:PageParameter.java
 * Date:2020-12-31 20:02:31
 */

package com.abluepoint.summer.mvc.domain;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

public class PageParameter {

    private int pageNo;
    private int pageSize;
    private String sortField;
    private String sortOrder;

    public PageParameter() {
    }

    public PageParameter(int pageNo, int pageSize, String sortField, String sortOrder) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.sortField = sortField;
        this.sortOrder = sortOrder;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getPageIndex() {
        if (pageNo == 0) {
            return 0;
        }
        return pageNo - 1;
    }

    public PageRequest toPageRequest(int defaultPageSize) {
        if (pageNo == 0) {
            pageNo = 1;
        }

        if (pageSize == 0) {
            pageSize = defaultPageSize;
        }

        if (StringUtils.hasText(getSortField())) {
            Sort sort;
            if ("ascend".equals(getSortOrder())) {
                sort = Sort.by(Sort.Direction.ASC, getSortField());
            } else {
                sort = Sort.by(Sort.Direction.DESC, getSortField());
            }
            return PageRequest.of(getPageIndex(), getPageSize(), sort);
        }
        return PageRequest.of(getPageIndex(), getPageSize());
    }

    public PageRequest toPageRequest() {
        return toPageRequest(10);
    }
}
