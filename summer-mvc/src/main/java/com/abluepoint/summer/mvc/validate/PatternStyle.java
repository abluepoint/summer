package com.abluepoint.summer.mvc.validate;

/*
 * Copyright (c) 2020 abluepoint All Rights Reserved.
 * File:PatternStyle.java
 * Date:2020-03-12 17:21:12
 */

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternStyle extends AbstractStyle {

    private static final ConversionService conversionService = DefaultConversionService.getSharedInstance();
    private Pattern pattern;

    public PatternStyle(Class<?> paramType, String desc, Map<String, String> settings, Pattern pattern) {
        super(paramType, desc, settings);
        this.pattern = pattern;
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public boolean validate(String value) {
        Matcher matcher =pattern.matcher(value);
        return matcher.matches();
    }

    @Override
    public Object getTypedObject(String value) {
        return conversionService.convert(value,getParamType());
    }
}
