package com.abluepoint.summer.mvc.manager;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ResourceJsonSchemaManager.java
 * Date:2020-03-18 17:56:18
 */

import org.apache.commons.io.IOUtils;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.Assert;

import java.io.IOException;

public class ResourceJsonSchemaManager extends AbstractJsonSchemaManager implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    private String schemaLocation;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    protected String getSchemaPath(String schemaPath) {
        Assert.hasText(schemaLocation, "schemaLocation must has text");
        Assert.hasText(schemaPath, "schemaPath must has text");
        StringBuilder pathBuilder = new StringBuilder(schemaLocation);
        if (!schemaLocation.endsWith("/")) {
            pathBuilder.append("/");
        }
        if (schemaPath.startsWith("/")) {
            schemaPath = schemaPath.substring(1);
        }
        pathBuilder.append(schemaPath);
        return pathBuilder.toString();
    }

    public void setSchemaLocation(String schemaLocation) {

        this.schemaLocation = schemaLocation;
    }

    public String getSchemaLocation() {
        return schemaLocation;
    }

    @Override
    protected String getSchemaContent(String schemaId) throws IOException {
        Resource resource = getResource(schemaId);
        if (resource == null) {
            return null;
        }
        return IOUtils.toString(resource.getInputStream(), "UTF-8");
    }

    private Resource getResource(String schemaId) throws IOException {
        String realPath = getSchemaPath(schemaId);
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        Resource[] resources = resourcePatternResolver.getResources(realPath);

        if (resources == null || resources.length == 0) {
            return null;
        }
        if (resources.length > 1) {
            throw new IOException("Duplicated schema file");
        }
        return resources[0];
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }
}
