package com.abluepoint.summer.mvc.manager;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonSchemaManagerImpl.java
 * Date:2020-03-18 16:11:18
 */

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import org.apache.commons.io.IOUtils;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

public abstract class AbstractJsonSchemaManager implements JsonSchemaManager {


    private SchemaValidatorsConfig validatorsConfig;
    private JsonSchemaFactory schemaFactory;


    public void setValidatorsConfig(SchemaValidatorsConfig validatorsConfig) {
        this.validatorsConfig = validatorsConfig;
    }


    @Override
    public JsonSchema getJsonSchema(String schemaId) throws IOException {
        return schemaFactory.getSchema(getSchemaContent(schemaId), validatorsConfig);
    }

    protected abstract String getSchemaContent(String schemaId) throws IOException ;


    public void setSchemaFactory(JsonSchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

}
