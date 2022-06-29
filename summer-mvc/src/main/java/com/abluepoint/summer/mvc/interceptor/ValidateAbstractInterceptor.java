package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.common.exception.SummerException;
import com.abluepoint.summer.mvc.annotation.FieldJsonSchema;
import com.abluepoint.summer.mvc.filter.CachingSupportServletRequestWrap;
import com.abluepoint.summer.mvc.manager.JsonSchemaManager;
import com.abluepoint.summer.mvc.validate.ValidateException;
import com.abluepoint.summer.mvc.validate.ValidateExceptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;

public abstract class ValidateAbstractInterceptor implements HandlerInterceptor {

    protected void validateJsonSchema(HttpServletRequest request, FieldJsonSchema fieldJsonSchema, ValidateExceptions exceptions, JsonSchemaManager jsonSchemaManager, ObjectMapper objectMapper) throws SummerException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            throw new ValidateException("-1.system.json_schema.http_method_must_be_post");
        }
        CachingSupportServletRequestWrap wrapper = WebUtils.getNativeRequest(request, CachingSupportServletRequestWrap.class);
        if (wrapper == null) {
            throw new SummerException("-1.system.request.not_cached");
        }
        if (!wrapper.isCached()) {
            wrapper.cacheRequest();
        }
        String schemaId = fieldJsonSchema.value();
        JsonSchema schema = jsonSchemaManager.getJsonSchema(schemaId);
        JsonNode node = objectMapper.readTree(wrapper.getByteArray());
        Set<ValidationMessage> errors = schema.validate(node);
        for (ValidationMessage error : errors) {
            exceptions.add(new ValidateException("400.json_schema." + error.getType(), error.getArguments()));
        }
        if (!exceptions.isEmpty()) {
            throw exceptions;
        }
    }
}
