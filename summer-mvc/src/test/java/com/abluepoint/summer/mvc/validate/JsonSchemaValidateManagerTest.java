/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:JsonSchemaValidateManagerTest.java
 * Date:2020-12-31 17:22:31
 */

package com.abluepoint.summer.mvc.validate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.FileSystemUtils;

import java.io.InputStream;
import java.util.Set;


public class JsonSchemaValidateManagerTest {

    @Test
    public void test() throws JsonProcessingException {
        SchemaValidatorsConfig config = new SchemaValidatorsConfig();
        config.setTypeLoose(false);
        /**
         */
        //        String schemaContent = "";
        InputStream is = getClass().getClassLoader().getResourceAsStream("test/schema/user.json");

        JsonSchemaFactory factory = JsonSchemaFactory.getInstance();
        JsonSchema schema = factory.getSchema(is,config);


        /**

         {
         "username":"abc",
         "password":"ad"
         }
         */
        String content = "{\n" + "         \"username\":\"abc\",\n" + "         \"password\":\"ad\"\n" + "         }";

        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        JsonNode node = objectMapper.readTree(content);
        Set<ValidationMessage> errors = schema.validate(node);
        for (ValidationMessage m : errors) {
            System.out.println("code=" + m.getCode());
            System.out.println("message=" + m.getMessage());
            System.out.println("path=" + m.getPath());
            System.out.println("type=" + m.getType());

            String[] args = m.getArguments();
            System.out.print("args=");
            if (args != null) {
                for (String arg : args) {
                    System.out.print(arg+",");
                }
            }
            System.out.println();

            System.out.println("detail=" + m.getDetails());
        }

    }

}