/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:IndexHtmlInterceptor.java
 * Date:2020-12-17 18:04:17
 */

package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.view.HtmlResourceView;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

public class IndexHtmlInterceptor implements HandlerInterceptor {

	private HtmlResourceView htmlResourceView;

	public IndexHtmlInterceptor(byte[] content, long lastModified) {
		this.htmlResourceView = new HtmlResourceView(content, lastModified);
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
		htmlResourceView.render(Collections.emptyMap(), request, response);
		return false;
	}
}