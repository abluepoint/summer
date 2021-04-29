/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ResourceJsonSchemaManagerTest.java
 * Date:2020-12-31 17:20:31
 */

package com.abluepoint.summer.mvc.manager;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;


public class ResourceJsonSchemaManagerTest {

    @Test
    public void test() throws IOException {
        ResourceJsonSchemaManager manager = new ResourceJsonSchemaManager();
        manager.setResourceLoader(new ResourceLoader() {

            @Override
            public Resource getResource(String location) {
                return new Resource() {

                    @Override
                    public boolean exists() {
                        return false;
                    }

                    @Override
                    public URL getURL() throws IOException {
                        return null;
                    }

                    @Override
                    public URI getURI() throws IOException {
                        return null;
                    }

                    @Override
                    public File getFile() throws IOException {
                        return null;
                    }

                    @Override
                    public long contentLength() throws IOException {
                        return 0;
                    }

                    @Override
                    public long lastModified() throws IOException {
                        return 0;
                    }

                    @Override
                    public Resource createRelative(String relativePath) throws IOException {
                        return null;
                    }

                    @Override
                    public String getFilename() {
                        return null;
                    }

                    @Override
                    public String getDescription() {
                        return null;
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return ResourceJsonSchemaManagerTest.class.getClassLoader().getResourceAsStream("test/schema/user.json");
                    }
                };
            }

            @Override
            public ClassLoader getClassLoader() {
                return ResourceJsonSchemaManagerTest.class.getClassLoader();
            }
        });

        manager.setSchemaLocation("121");
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance();
        manager.setSchemaFactory(factory);
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setTypeLoose(false);
        manager.setValidatorsConfig(config);

        JsonSchema schema = manager.getJsonSchema("2121");
        System.out.println(schema);
    }

}