/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ResourceViewFactory.java
 * Date:2020-12-17 12:35:17
 */

package com.abluepoint.summer.mvc.view;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ResourceRegionHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.HttpResource;

public class ResourceViewFactory implements InitializingBean, ApplicationContextAware {

    private ResourceHttpMessageConverter resourceHttpMessageConverter;
    private ResourceRegionHttpMessageConverter resourceRegionHttpMessageConverter;
    private ApplicationContext applicationContext;


    public ResourceView getResouceView(Resource resource,MediaType mediaType) {
        ResourceView ResourceView = new ResourceView(resource, mediaType,this);
        return ResourceView;
    }

    public void render(ResourceView resourceView, HttpServletRequest request, HttpServletResponse response) throws Exception {
        Resource resource = resourceView.getResource();
        if (resource == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (new ServletWebRequest(request, response).checkNotModified(resource.lastModified())) {
            return;
        }
        ServletServerHttpResponse outputMessage = new ServletServerHttpResponse(response);
        if (request.getHeader(HttpHeaders.RANGE) == null) {
            Assert.state(this.resourceHttpMessageConverter != null, "Not initialized");
            setHeaders(response, resource, resourceView.getMediaType());
            this.resourceHttpMessageConverter.write(resource, resourceView.getMediaType(), outputMessage);
        } else {
            Assert.state(this.resourceRegionHttpMessageConverter != null, "Not initialized");
            response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
            ServletServerHttpRequest inputMessage = new ServletServerHttpRequest(request);
            try {
                List<HttpRange> httpRanges = inputMessage.getHeaders().getRange();
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                this.resourceRegionHttpMessageConverter.write(HttpRange.toResourceRegions(httpRanges, resource), MediaType.TEXT_HTML, outputMessage);
            } catch (IllegalArgumentException ex) {
                response.setHeader("Content-Range", "bytes */" + resource.contentLength());
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            }
        }
    }

    public ResourceHttpMessageConverter getResourceHttpMessageConverter() {
        return resourceHttpMessageConverter;
    }

    public void setResourceHttpMessageConverter(ResourceHttpMessageConverter resourceHttpMessageConverter) {
        this.resourceHttpMessageConverter = resourceHttpMessageConverter;
    }

    public ResourceRegionHttpMessageConverter getResourceRegionHttpMessageConverter() {
        return resourceRegionHttpMessageConverter;
    }

    public void setResourceRegionHttpMessageConverter(ResourceRegionHttpMessageConverter resourceRegionHttpMessageConverter) {
        this.resourceRegionHttpMessageConverter = resourceRegionHttpMessageConverter;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.resourceHttpMessageConverter == null) {
            this.resourceHttpMessageConverter = new ResourceHttpMessageConverter();
        }
        if (this.resourceRegionHttpMessageConverter == null) {
            this.resourceRegionHttpMessageConverter = new ResourceRegionHttpMessageConverter();
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    protected void setHeaders(HttpServletResponse response, Resource resource, @Nullable MediaType mediaType) throws IOException {

        long length = resource.contentLength();
        if (length > Integer.MAX_VALUE) {
            response.setContentLengthLong(length);
        } else {
            response.setContentLength((int) length);
        }

        if (mediaType != null) {
            response.setContentType(mediaType.toString());
        }
        if (resource instanceof HttpResource) {
            HttpHeaders resourceHeaders = ((HttpResource) resource).getResponseHeaders();
            resourceHeaders.forEach((headerName, headerValues) -> {
                boolean first = true;
                for (String headerValue : headerValues) {
                    if (first) {
                        response.setHeader(headerName, headerValue);
                    } else {
                        response.addHeader(headerName, headerValue);
                    }
                    first = false;
                }
            });
        }
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
    }

}
