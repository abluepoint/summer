package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:DefaultAnnotationManager.java
 * Date:2020-03-10 17:09:10
 */

import org.springframework.web.method.HandlerMethod;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultAnnotationMetadataManager implements AnnotationMetadataManager {

    private final Map<String, Optional<Interceptors>> interceptorsCache;
    private final Map<String, FieldsMeta> fieldsCache;
    private final Map<String, Optional<FieldJsonSchema>> fieldJsonScemaCache;

    private DefaultAnnotationMetadataManager() {
        interceptorsCache = new ConcurrentHashMap<>();
        fieldsCache = new ConcurrentHashMap<>();
        fieldJsonScemaCache = new ConcurrentHashMap<>();
    }

    private static AnnotationMetadataManager annotationMetadataManager;

    public static AnnotationMetadataManager getInstance() {
        if (annotationMetadataManager == null) {
            synchronized (DefaultAnnotationMetadataManager.class) {
                if (annotationMetadataManager == null) {
                    annotationMetadataManager = new DefaultAnnotationMetadataManager();
                }
            }
        }
        return annotationMetadataManager;
    }

    @Override
    public Interceptors getInterceptors(HandlerMethod handler) {
        String key = handler.toString();
        Optional<Interceptors> optional = interceptorsCache.get(key);
        if (optional != null) {
            if (optional.isPresent()) {
                return optional.get();
            } else {
                return null;
            }
        } else {
            Interceptors interceptors = handler.getMethodAnnotation(Interceptors.class);
            interceptorsCache.put(key, Optional.ofNullable(interceptors));
            return interceptors;
        }
    }

    @Override
    public FieldJsonSchema getFieldJsonSchemaMeta(HandlerMethod handler) {
        String key = handler.toString();
        Optional<FieldJsonSchema> optional = fieldJsonScemaCache.get(key);
        if (optional != null) {
            if (optional.isPresent()) {
                return optional.get();
            } else {
                return null;
            }
        } else {
            FieldJsonSchema fieldJsonSchema = handler.getMethodAnnotation(FieldJsonSchema.class);
            fieldJsonScemaCache.put(key, Optional.ofNullable(fieldJsonSchema));
            return fieldJsonSchema;
        }
    }

    @Override
    public FieldsMeta getFieldsMeta(HandlerMethod hm) {
        String key = hm.toString();
        FieldsMeta fieldsMeta = fieldsCache.get(key);
        if (fieldsMeta != null) {
            return fieldsMeta;
        } else {
            fieldsMeta = createFieldsMeta(hm);
            fieldsCache.put(key, fieldsMeta);
            return fieldsMeta;
        }
    }

    private FieldsMeta createFieldsMeta(HandlerMethod hm) {
        Fields fields = hm.getMethodAnnotation(Fields.class);
        FieldMeta[] fieldMetaArray = null;
        if (fields != null) {
            fieldMetaArray = createFieldMetaArray(fields);
        } else {
            Field field = hm.getMethodAnnotation(Field.class);
            if (field != null) {
                fieldMetaArray = createFieldMetaArray(field);
            }
        }

        FieldLists fieldLists = hm.getMethodAnnotation(FieldLists.class);
        FieldListMeta[] fieldListMetaArray = null;
        if (fieldLists != null) {
            FieldList[] fieldListArray = fieldLists.value();
            fieldListMetaArray = new FieldListMeta[fieldListArray.length];
            for (int i = 0; i < fieldListArray.length; i++) {
                fieldListMetaArray[i] = new FieldListMeta(fieldListArray[i], createFieldListMetaArray(fieldListArray[i]));
            }
        } else {
            FieldList fieldList = hm.getMethodAnnotation(FieldList.class);
            if (fieldList != null) {
                fieldListMetaArray = new FieldListMeta[1];
                fieldListMetaArray[0] = new FieldListMeta(fieldList, createFieldListMetaArray(fieldList));
            }
        }
        return new FieldsMeta(fieldMetaArray, fieldListMetaArray);
    }

    private FieldMeta[] createFieldMetaArray(Fields fields) {
        Field[] fieldArray = fields.value();
        FieldMeta[] fieldMetaArray = new FieldMeta[fieldArray.length];
        for (int i = 0; i < fieldArray.length; i++) {
            fieldMetaArray[i] = new FieldMeta(fieldArray[i]);
        }
        return fieldMetaArray;
    }

    private FieldMeta[] createFieldMetaArray(Field field) {
        FieldMeta[] fieldMetaArray = new FieldMeta[1];
        fieldMetaArray[0] = new FieldMeta(field);
        return fieldMetaArray;
    }

    private FieldMeta[] createFieldListMetaArray(FieldList fields) {
        Item[] items = fields.items();
        FieldMeta[] fieldMetaArray = new FieldMeta[items.length];
        for (int i = 0; i < items.length; i++) {
            fieldMetaArray[i] = new FieldMeta(items[i]);
        }
        return fieldMetaArray;
    }

}
