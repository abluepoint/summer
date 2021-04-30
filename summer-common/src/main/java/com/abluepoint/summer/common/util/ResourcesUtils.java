package com.abluepoint.summer.common.util;

import com.abluepoint.summer.common.io.StringBuilderWriter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.Assert;

import java.io.IOException;

public abstract class ResourcesUtils {

    public static Resource[] getResources(String sourcePattern, ResourceLoader resourceLoader) throws IOException {
        PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        Resource[] resources = resourcePatternResolver.getResources(sourcePattern);
        return resources;
    }

    public static String pathConcat(String leftPath, String rightPath) {
        Assert.notNull(leftPath, "left path is null");
        Assert.notNull(rightPath, "right path is null");

        StringBuilderWriter writer = new StringBuilderWriter(leftPath.length() + rightPath.length());
        writer.write(leftPath);
        if (!leftPath.endsWith("/")) {
            writer.write("/");
        }
        if (rightPath.startsWith("/")) {
            writer.write(rightPath,1,rightPath.length()-1);
        }else{
            writer.write(rightPath);
        }
        return writer.toString();
    }

}
