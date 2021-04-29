package com.abluepoint.summer.mvc.manager;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:CachedSchemaManager.java
 * Date:2020-03-18 18:16:18
 */

import com.networknt.schema.JsonSchema;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachedSchemaManager implements JsonSchemaManager {

    private JsonSchemaManager delegate;
    private Map<String,JsonSchema> cacheMap = new ConcurrentHashMap<>();

    public CachedSchemaManager(JsonSchemaManager delegate) {
        this.delegate = delegate;
        cacheMap = new ConcurrentHashMap<>();
    }

    @Override
    public JsonSchema getJsonSchema(String schemaId) throws IOException {
        JsonSchema jsonSchema = cacheMap.get(schemaId);
        if(jsonSchema==null){
            jsonSchema = delegate.getJsonSchema(schemaId);
            cacheMap.put(schemaId,jsonSchema);
        }
        return jsonSchema;
    }
}
