/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateOnlyInterceptor.java
 * Date:2020-03-31 23:36:31
 */

package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.common.exception.CodeMessageEnum;
import com.abluepoint.summer.common.util.AppContextUtil;
import com.abluepoint.summer.mvc.annotation.*;
import com.abluepoint.summer.mvc.manager.JsonSchemaManager;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.ValidateException;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidateOnlyInterceptor extends ValidateAbstractInterceptor {

    private static final AnnotationMetadataManager metadataManager = AnnotationMetadataManager.INSTANCE;
    /**
     * 只能是数字 ^\d*$
     */
    private final Pattern countPattern = Pattern.compile("^\\d*$");

    private JsonSchemaManager jsonSchemaManager;

    private ObjectMapper objectMapper;

    public ValidateOnlyInterceptor(JsonSchemaManager jsonSchemaManager, ObjectMapper objectMapper) {
        this.jsonSchemaManager = jsonSchemaManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod hm = (HandlerMethod) handler;

        FieldJsonSchema fieldJsonSchema = metadataManager.getFieldJsonSchemaMeta(hm);
        FieldsMeta fieldsMeta = metadataManager.getFieldsMeta(hm);

        boolean isJsonSchema = fieldJsonSchema != null;

        ValidateExceptions exceptions = new ValidateExceptions();

        if (fieldsMeta.hasFields()) {

            if (fieldsMeta.hasField()) {
                FieldMeta[] fields = fieldsMeta.getFields();
                FieldMeta field = null;
                String paramName = null;
                String value = null;
                for (int i = 0; i < fields.length; i++) {
                    field = fields[i];
                    paramName = field.getParam();
                    value = request.getParameter(paramName);
                    validateField(field, paramName, value, exceptions);
                }
            }

            if (fieldsMeta.hasFieldList()) {
                FieldListMeta[] fieldLists = fieldsMeta.getFieldLists();
                FieldListMeta fieldList = null;
                for (int i = 0; i < fieldLists.length; i++) {
                    fieldList = fieldLists[i];
                    String countStr = request.getParameter(fieldList.getCountParam());
                    Matcher matcher = countPattern.matcher(countStr);
                    if (!matcher.matches()) {
                        exceptions.add(new ValidateException(CodeMessageEnum.VALIDATE_FIELD_LIST_ILLEGAL_COUNT, new Object[] { fieldList.getName(), countStr }));
                    } else {
                        int count = Integer.parseInt(countStr);
                        FieldMeta[] items = fieldList.getFields();

                        Map<String, Object> itemValue = null;
                        for (int c = 0; c < count; c++) {
                            for (int j = 0; j < items.length; j++) {
                                FieldMeta field = items[j];
                                String paramName = getItemParamName(fieldList, field, c);
                                String value = request.getParameter(paramName);
                                validateField(field, paramName, value, exceptions);
                            }
                        }
                    }
                }
            }
            if (!exceptions.isEmpty()) {
                throw exceptions;
            }
        }

        if(isJsonSchema){
            validateJsonSchema(request, fieldJsonSchema, exceptions, jsonSchemaManager, objectMapper);
        }
        return true;
    }



    protected void validateField(FieldMeta field, String paramName, String value, ValidateExceptions exceptions) throws ValidateException {

        Style style = field.getStyle();
        if (style == null) {
            style = getStyle(field.getStyleName());
            field.setStyle(style);
        }

        if (field.isRequired()) {
            if (StringUtils.isEmpty(value)) {
                exceptions.add(new ValidateException(CodeMessageEnum.VALIDATE_FIELD_REQUIRED, new Object[] { paramName, value, field.getStyle().getDesc() }));
            }
        }

        if (value != null) {
            if (!style.validate(value)) {
                exceptions.add(new ValidateException(CodeMessageEnum.VALIDATE_FIELD_ERROR, new Object[] { paramName, value, field.getStyle().getDesc() }));
            }
        }
    }


    protected Style getStyle(String styleName) {
        return AppContextUtil.getBean(styleName, Style.class);
    }

    protected String getItemParamName(FieldListMeta fieldList, FieldMeta item, int index) {
        return fieldList.getParam() + "[" + index + "]." + item.getParam();
    }

    public JsonSchemaManager getJsonSchemaManager() {
        return jsonSchemaManager;
    }

    public void setJsonSchemaManager(JsonSchemaManager jsonSchemaManager) {
        this.jsonSchemaManager = jsonSchemaManager;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
