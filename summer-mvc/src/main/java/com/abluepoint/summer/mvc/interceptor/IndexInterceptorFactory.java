/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:IndexInterceptorFactory.java
 * Date:2020-12-29 17:21:29
 */

package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.view.ResourceViewFactory;
import org.apache.commons.io.IOUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.InputStream;

public class IndexInterceptorFactory  {

	private ApplicationContext applicationContext;
	private ResourceViewFactory resourceViewFactory;

	public IndexInterceptorFactory(ApplicationContext applicationContext, ResourceViewFactory resourceViewFactory) {
		this.applicationContext = applicationContext;
		this.resourceViewFactory = resourceViewFactory;
	}

	public HandlerInterceptor getIndexInterceptor(String indexPath, long lastModified) {

		if (indexPath.startsWith("file:")) {
			Resource resource = null;
			resource = applicationContext.getResource(indexPath);
			return new StaticResourceLoaderInterceptor(resourceViewFactory.getResouceView(resource, MediaType.TEXT_HTML));

		} else {
			try {
				indexPath = indexPath.replace("classpath:", "/");
				InputStream inputStream = IndexInterceptorFactory.class.getResourceAsStream(indexPath);
				byte[] content = IOUtils.toByteArray(inputStream);
				return new IndexHtmlInterceptor(content, System.currentTimeMillis());
			} catch (IOException e) {
				throw new RuntimeException("load file error",e);
			}
		}
	}

}