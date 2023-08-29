/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ResourceView.java
 * Date:2020-12-17 12:34:17
 */

package com.abluepoint.summer.mvc.view;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.View;

import java.util.Map;

public class ResourceView implements View {

    private Resource resource;
    private MediaType mediaType;
    private ResourceViewFactory resourceViewFactory;

    ResourceView(Resource resource,MediaType mediaType, ResourceViewFactory resourceViewFactory) {
        this.resource = resource;
        this.mediaType = mediaType;
        this.resourceViewFactory = resourceViewFactory;
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        resourceViewFactory.render(this, request, response);
    }

    public Resource getResource() {
        return resource;
    }

    public MediaType getMediaType() {
        return mediaType;
    }
}
