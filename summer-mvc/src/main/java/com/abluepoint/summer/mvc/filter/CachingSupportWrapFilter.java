/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:CachingSupportWrapFilter.java
 * Date:2020-09-10 14:50:10
 */

package com.abluepoint.summer.mvc.filter;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CachingSupportWrapFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        CachingSupportServletRequestWrap requestWrap = new CachingSupportServletRequestWrap(request);
//        requestWrap.cacheRequest();
        filterChain.doFilter(requestWrap, response);
    }
}
