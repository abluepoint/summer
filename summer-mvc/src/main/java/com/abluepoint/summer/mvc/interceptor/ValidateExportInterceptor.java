/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:ValidateExportInterceptor.java
 * Date:2020-12-30 17:30:30
 */

package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.common.exception.SummerException;
import com.abluepoint.summer.common.util.AppContextUtil;
import com.abluepoint.summer.mvc.annotation.*;
import com.abluepoint.summer.mvc.context.Context;
import com.abluepoint.summer.mvc.filter.CachingSupportServletRequestWrap;
import com.abluepoint.summer.mvc.manager.JsonSchemaManager;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.ValidateException;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidateExportInterceptor implements HandlerInterceptor {

    private static final AnnotationMetadataManager metadataManager = AnnotationMetadataManager.INSTANCE;
    /**
     * 只能是数字 ^\d*$
     */
    private final Pattern countPattern = Pattern.compile("^\\d*$");

    private JsonSchemaManager jsonSchemaManager;

    private ObjectMapper objectMapper;

    public ValidateExportInterceptor(JsonSchemaManager jsonSchemaManager, ObjectMapper objectMapper) {
        this.jsonSchemaManager = jsonSchemaManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod hm = (HandlerMethod) handler;

        FieldsMeta fieldsMeta = metadataManager.getFieldsMeta(hm);
        FieldJsonSchema fieldJsonSchema = metadataManager.getFieldJsonSchemaMeta(hm);

        boolean isJsonSchema = fieldJsonSchema != null;
        if (isJsonSchema && fieldsMeta.hasFields()) {
            throw new ValidateException("validate.field.illegal_config_both");
        }

        ValidateExceptions exceptions = new ValidateExceptions();

        Map<String, Object> data = initDataContainer(fieldsMeta.count());

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
                    validateField(field, paramName, value, data, exceptions);
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
                        exceptions.add(new ValidateException("validate.field.list.illegal.count", new Object[] { fieldList.getName(), countStr }));
                    } else {
                        int count = Integer.parseInt(countStr);
                        FieldMeta[] items = fieldList.getFields();

                        List<Map<String, Object>> valueList = new ArrayList<>(count);
                        Map<String, Object> itemValue = null;
                        for (int c = 0; c < count; c++) {
                            itemValue = initItemDataContainer(items.length);
                            for (int j = 0; j < items.length; j++) {
                                FieldMeta field = items[j];
                                String paramName = getItemParamName(fieldList, field, c);
                                String value = request.getParameter(paramName);
                                validateField(field, paramName, value, itemValue, exceptions);
                            }
                            valueList.add(itemValue);
                        }
                    }
                }
            }
            if (!exceptions.isEmpty()) {
                throw exceptions;
            }
        }

        if (isJsonSchema) {
            if (!HttpMethod.POST.matches(request.getMethod())) {
                throw new ValidateException("field.config.json_schema.http_method_must_be_post");
            }
            CachingSupportServletRequestWrap wrapper = WebUtils.getNativeRequest(request, CachingSupportServletRequestWrap.class);
            if (wrapper == null) {
                throw new SummerException("system.request.not_cached");
            }
            if(!wrapper.isCached()){
                wrapper.cacheRequest();
            }
            String schemaId = fieldJsonSchema.value();
            JsonSchema schema = jsonSchemaManager.getJsonSchema(schemaId);
            JsonNode node = objectMapper.readTree(wrapper.getByteArray());
            Set<ValidationMessage> errors = schema.validate(node);
            for (ValidationMessage error : errors) {
                exceptions.add(new ValidateException("validate.json." + error.getType(), error.getArguments()));
            }
            if (!exceptions.isEmpty()) {
                throw exceptions;
            }
        }

        request.setAttribute(Context.DATA_ATTR, data);

        return true;
    }

    protected HashMap<String, Object> initDataContainer(int oriSize) {
        return new HashMap<>(oriSize + 10);
    }

    protected HashMap<String, Object> initItemDataContainer(int oriSize) {
        return new HashMap<>(oriSize + 10);
    }

    protected void validateField(FieldMeta field, String paramName, String value, Map<String, Object> data, ValidateExceptions exceptions) throws ValidateException {

        Style style = field.getStyle();
        if (style == null) {
            style = getStyle(field.getStyleName());
            field.setStyle(style);
        }

        if (field.isRequired()) {
            if (StringUtils.isEmpty(value)) {
                exceptions.add(new ValidateException("validate.field.required", new Object[] { paramName, value, field.getStyle().getDesc() }));
            }
        }

        if (value != null) {
            if (style.validate(value)) {
                String name = field.getName();
                if(StringUtils.hasText(name)){
                    data.put(name, style.getTypedObject(value));
                }
            } else {
                exceptions.add(new ValidateException("validate.field.error", new Object[] { paramName, value, field.getStyle().getDesc() }));
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
