package com.abluepoint.summer.mvc.validate;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.FieldSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;

public final class JsonFieldCachingValidator implements SmartInitializingSingleton {

    private final List<RequestMappingHandlerMapping> handlerMappings;

    public JsonFieldCachingValidator(List<RequestMappingHandlerMapping> handlerMappings) {
        this.handlerMappings = List.copyOf(handlerMappings);
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (RequestMappingHandlerMapping handlerMapping : handlerMappings) {
            for (HandlerMethod handlerMethod : handlerMapping.getHandlerMethods().values()) {
                if (requiresJsonBody(handlerMethod.getMethod())) {
                    throw new IllegalStateException(
                            "JSON Field validation requires summer.mvc.cache-request-body=true: "
                                    + handlerMethod.getShortLogMessage()
                    );
                }
            }
        }
    }

    private boolean requiresJsonBody(Method method) {
        FieldContract contract = AnnotatedElementUtils.findMergedAnnotation(method, FieldContract.class);
        if (contract != null && contract.rejectUnknownJson()) {
            return true;
        }
        boolean jsonField = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Field.class)
                .stream()
                .anyMatch(field -> field.source() == FieldSource.JSON);
        boolean jsonFieldList = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, FieldList.class)
                .stream()
                .anyMatch(fieldList -> fieldList.source() == FieldSource.JSON);
        return jsonField || jsonFieldList;
    }
}
