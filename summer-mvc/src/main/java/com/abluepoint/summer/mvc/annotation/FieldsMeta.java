package com.abluepoint.summer.mvc.annotation;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:FieldsMeta.java
 * Date:2020-03-11 09:55:11
 */

import java.util.Arrays;

public class FieldsMeta {

    private final FieldMeta[] fields;
    private final FieldListMeta[] fieldLists;

    private final boolean hasFields;
    private final boolean hasField;
    private final boolean hasFieldList;
    private final int count;

    public FieldsMeta(FieldMeta[] fields, FieldListMeta[] fieldLists) {
        this.fields = fields;
        this.hasField = (fields != null);
        this.fieldLists = fieldLists;
        this.hasFieldList = (fieldLists != null);
        this.hasFields = hasField || hasFieldList;
        int temp =0;
        if(this.hasField){
            temp = temp+fields.length;
        }
        if(this.hasFieldList()){
            temp = temp+fieldLists.length;
        }
        this.count = temp;
    }

    public FieldMeta[] getFields() {
        return fields;
    }

    public FieldListMeta[] getFieldLists() {
        return fieldLists;
    }

    public int count() {
        return count;
    }

    public boolean hasFields() {
        return hasFields;
    }

    public boolean hasField() {
        return hasField;
    }

    public boolean hasFieldList() {
        return hasFieldList;
    }

    @Override
    public String toString() {
        return "FieldsMeta{" + "fields=" + Arrays.toString(fields) + ", fieldLists=" + Arrays.toString(fieldLists) + '}';
    }
}
