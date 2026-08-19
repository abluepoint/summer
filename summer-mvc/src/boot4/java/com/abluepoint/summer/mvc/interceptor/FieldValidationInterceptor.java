package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.Item;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.Style;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FieldValidationInterceptor implements HandlerInterceptor {

    private final StyleRegistry styleRegistry;
    private final int maxListSize;

    public FieldValidationInterceptor(StyleRegistry styleRegistry, int maxListSize) {
        this.styleRegistry = Objects.requireNonNull(styleRegistry, "styleRegistry");
        if (maxListSize < 1) {
            throw new IllegalArgumentException("maxListSize must be positive");
        }
        this.maxListSize = maxListSize;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Map<String, Object> values = new LinkedHashMap<>();
        List<FieldValidationException.Violation> violations = new ArrayList<>();
        for (Field field : AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(),
                Field.class
        )) {
            readField(request, field.param(), field.name(), field.required(), field.style(), values, violations);
        }
        for (FieldList fieldList : AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(),
                FieldList.class
        )) {
            if (fieldList.items().length == 0) {
                throw new IllegalStateException("FieldList items must not be empty: " + fieldList.param());
            }
            String countParam = StringUtils.hasText(fieldList.countParam())
                    ? fieldList.countParam()
                    : fieldList.param() + ".count";
            Integer count = readCount(request.getParameter(countParam), countParam, violations);
            if (count == null) {
                continue;
            }
            int configuredMax = fieldList.maxSize() > 0 ? fieldList.maxSize() : maxListSize;
            int effectiveMax = Math.min(configuredMax, maxListSize);
            if (count > effectiveMax) {
                violations.add(new FieldValidationException.Violation(
                        countParam,
                        "must be at most " + effectiveMax
                ));
                continue;
            }

            List<Map<String, Object>> itemValues = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Map<String, Object> itemValuesAtIndex = new LinkedHashMap<>();
                for (Item item : fieldList.items()) {
                    String param = fieldList.param() + "[" + index + "]." + item.param();
                    readField(
                            request,
                            param,
                            item.name(),
                            item.required(),
                            item.style(),
                            itemValuesAtIndex,
                            violations
                    );
                }
                itemValues.add(itemValuesAtIndex);
            }
            String name = StringUtils.hasText(fieldList.name()) ? fieldList.name() : fieldList.param();
            values.put(name, List.copyOf(itemValues));
        }
        if (!violations.isEmpty()) {
            throw new FieldValidationException(violations);
        }
        request.setAttribute(FieldValues.REQUEST_ATTRIBUTE, new FieldValues(values));
        return true;
    }

    private void readField(
            HttpServletRequest request,
            String param,
            String name,
            boolean required,
            String styleName,
            Map<String, Object> values,
            List<FieldValidationException.Violation> violations
    ) {
        String rawValue = request.getParameter(param);
        if (!StringUtils.hasText(rawValue)) {
            if (required) {
                violations.add(new FieldValidationException.Violation(param, "is required"));
            }
            return;
        }

        Style style = styleRegistry.require(styleName);
        if (!style.validate(rawValue)) {
            violations.add(new FieldValidationException.Violation(param, "must be " + style.getDesc()));
            return;
        }
        try {
            values.put(StringUtils.hasText(name) ? name : param, style.getTypedObject(rawValue));
        } catch (RuntimeException exception) {
            violations.add(new FieldValidationException.Violation(param, "must be " + style.getDesc()));
        }
    }

    private Integer readCount(
            String rawCount,
            String countParam,
            List<FieldValidationException.Violation> violations
    ) {
        if (!StringUtils.hasText(rawCount)) {
            violations.add(new FieldValidationException.Violation(countParam, "is required"));
            return null;
        }
        try {
            int count = Integer.parseInt(rawCount);
            if (count < 0) {
                throw new NumberFormatException("negative count");
            }
            return count;
        } catch (NumberFormatException exception) {
            violations.add(new FieldValidationException.Violation(
                    countParam,
                    "must be a non-negative integer"
            ));
            return null;
        }
    }
}
