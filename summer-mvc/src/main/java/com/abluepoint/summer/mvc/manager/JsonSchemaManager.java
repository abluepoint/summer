package com.abluepoint.summer.mvc.manager;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonSchemaValidateManager.java
 * Date:2020-03-18 11:27:18
 */

import com.networknt.schema.JsonSchema;

import java.io.IOException;

public interface JsonSchemaManager {

    JsonSchema getJsonSchema(String schemaId) throws IOException;

}
