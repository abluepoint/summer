package com.abluepoint.summer.mvc.validate;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.Item;
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

    public StyleReferenceValidator(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            StyleRegistry styleRegistry
    ) {
        this.handlerMappings = handlerMappings;
        this.styleRegistry = styleRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> missing = new ArrayList<>();
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings.orderedStream().toList()) {
            for (HandlerMethod handlerMethod : handlerMapping.getHandlerMethods().values()) {
                validate(handlerMethod, missing);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Unknown Style references found at startup:\n" + String.join("\n", missing)
            );
        }
    }

    private void validate(HandlerMethod handlerMethod, List<String> missing) {
        String handler = handlerMethod.getShortLogMessage();
        for (Field field : AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(), Field.class
        )) {
            require(handler, field.param(), field.style(), missing);
        }
        for (FieldList fieldList : AnnotatedElementUtils.getMergedRepeatableAnnotations(
                handlerMethod.getMethod(), FieldList.class
        )) {
            for (Item item : fieldList.items()) {
                require(handler, fieldList.param() + "." + item.param(), item.style(), missing);
            }
        }
    }

    private void require(String handler, String field, String style, List<String> missing) {
        if (!StringUtils.hasText(style) || !styleRegistry.contains(style)) {
            missing.add("handler=" + handler + ", field=" + field + ", style=" + style);
        }
    }
}
