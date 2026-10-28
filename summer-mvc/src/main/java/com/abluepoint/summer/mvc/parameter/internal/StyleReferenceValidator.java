package com.abluepoint.summer.mvc.parameter.internal;

import com.abluepoint.summer.mvc.parameter.spi.Style;

import com.abluepoint.summer.mvc.parameter.api.Field;
import com.abluepoint.summer.mvc.parameter.api.FieldList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;

/** Validates every Field and FieldList Style reference once MVC mappings exist. */
public final class StyleReferenceValidator implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final StyleRegistry styleRegistry;
    private final int maxListSize;

    public StyleReferenceValidator(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            StyleRegistry styleRegistry
    ) {
        this(handlerMappings, styleRegistry, 100);
    }

    public StyleReferenceValidator(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            StyleRegistry styleRegistry,
            int maxListSize
    ) {
        this.handlerMappings = handlerMappings;
        this.styleRegistry = styleRegistry;
        if (maxListSize < 1) {
            throw new IllegalArgumentException("maxListSize must be positive");
        }
        this.maxListSize = maxListSize;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> missing = new ArrayList<>();
        List<String> invalidLists = new ArrayList<>();
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings.orderedStream().toList()) {
            for (HandlerMethod handlerMethod : handlerMapping.getHandlerMethods().values()) {
                validate(handlerMethod, missing, invalidLists);
            }
        }
        if (!missing.isEmpty() || !invalidLists.isEmpty()) {
            StringBuilder message = new StringBuilder();
            if (!missing.isEmpty()) {
                message.append("Unknown Style references found at startup:\n")
                        .append(String.join("\n", missing));
            }
            if (!invalidLists.isEmpty()) {
                if (!message.isEmpty()) {
                    message.append("\n");
                }
                message.append("Invalid FieldList configurations found at startup:\n")
                        .append(String.join("\n", invalidLists));
            }
            throw new IllegalStateException(message.toString());
        }
    }

    private void validate(HandlerMethod handlerMethod, List<String> missing, List<String> invalidLists) {
        String handler = handlerMethod.getShortLogMessage();
        for (Field field : AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(), Field.class
        )) {
            require(handler, field.param(), field.style(), missing);
        }
        for (FieldList fieldList : AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(), FieldList.class
        )) {
            validateList(handler, fieldList, invalidLists);
            for (Field field : fieldList.items()) {
                require(handler, fieldList.param() + "." + field.param(), field.style(), missing);
            }
        }
    }


    private void validateList(
            String handler,
            FieldList fieldList,
            List<String> invalidLists
    ) {
        int max = fieldList.maxSize() == -1 ? maxListSize : fieldList.maxSize();
        if (fieldList.items().length == 0) {
            invalidLists.add("handler=" + handler + ", field=" + fieldList.param()
                    + ", items must not be empty");
        }
        if (fieldList.minSize() < 1) {
            invalidLists.add("handler=" + handler + ", field=" + fieldList.param()
                    + ", minSize=" + fieldList.minSize() + " must be at least 1");
        }
        if (fieldList.maxSize() == 0 || fieldList.maxSize() < -1) {
            invalidLists.add("handler=" + handler + ", field=" + fieldList.param()
                    + ", maxSize=" + fieldList.maxSize() + " must be -1 or positive");
        }
        if (fieldList.minSize() >= 1 && max >= 1 && fieldList.minSize() > max) {
            invalidLists.add("handler=" + handler + ", field=" + fieldList.param()
                    + ", minSize=" + fieldList.minSize() + " exceeds effective maxSize=" + max);
        }
    }

    private void require(String handler, String field, String style, List<String> missing) {
        if (!StringUtils.hasText(style) || !styleRegistry.contains(style)) {
            missing.add("handler=" + handler + ", field=" + field + ", style=" + style);
        }
    }
}
