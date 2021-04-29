/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ResourceUtils.java
 * Date:2020-04-03 23:31:03
 */

package com.abluepoint.summer.common.util;

import org.slf4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
public abstract class ResourceLoaderHelper {

    public static Resource[] getResources(String locationPattern, ResourceLoader resourceLoader) throws IOException {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        Resource[] resources = resourcePatternResolver.getResources(locationPattern);
        return resources;
    }

    public static Resource getResource(String location, ResourceLoader resourceLoader) throws IOException {
        Resource[] resources = getResources(location,resourceLoader);
        if(resources.length==0){
            return null;
        }else if(resources.length==1){
            return resources[0];
        }else {
            throw new IllegalArgumentException("resource location duplicated: "+location);
        }
    }

}
