package com.abluepoint.summer.mvc.validate;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class PatternStyle extends AbstractStyle {

    private static final ConversionService CONVERSION_SERVICE = DefaultConversionService.getSharedInstance();

    private final Pattern pattern;

    public PatternStyle(Class<?> paramType, String desc, Map<String, String> settings, Pattern pattern) {
        super(Objects.requireNonNull(paramType, "paramType"), Objects.requireNonNull(desc, "desc"), settings);
        this.pattern = Objects.requireNonNull(pattern, "pattern");
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public boolean validate(String value) {
        return value != null && pattern.matcher(value).matches();
    }

    @Override
    public Object getTypedObject(String value) {
        return CONVERSION_SERVICE.convert(value, getParamType());
    }
}
