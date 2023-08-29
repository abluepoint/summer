package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.common.exception.CodeMessageDefault;
import com.abluepoint.summer.common.exception.CodeMessageEnum;
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

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;

public abstract class ValidateAbstractInterceptor implements HandlerInterceptor {

    protected void validateJsonSchema(HttpServletRequest request, FieldJsonSchema fieldJsonSchema, ValidateExceptions exceptions, JsonSchemaManager jsonSchemaManager, ObjectMapper objectMapper) throws SummerException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            throw new ValidateException(CodeMessageEnum.VALIDATE_ILLEGAL_JSON_HTTP_METHOD);
        }
        CachingSupportServletRequestWrap wrapper = WebUtils.getNativeRequest(request, CachingSupportServletRequestWrap.class);
        if (wrapper == null) {
            throw new SummerException(CodeMessageEnum.SYSTEM_REQUEST_NOT_CACHED);
        }
        if (!wrapper.isCached()) {
            wrapper.cacheRequest();
        }
        String schemaId = fieldJsonSchema.value();
        JsonSchema schema = jsonSchemaManager.getJsonSchema(schemaId);
        JsonNode node = objectMapper.readTree(wrapper.getByteArray());
        Set<ValidationMessage> errors = schema.validate(node);
        for (ValidationMessage error : errors) {
            CodeMessageDefault codeMessageDefault = new CodeMessageDefault(CodeMessageEnum.VALIDATE_ERROR.getCode(),"validate.json_schema."+error.getType());
            exceptions.add(new ValidateException(codeMessageDefault, error.getArguments()));
        }
        if (!exceptions.isEmpty()) {
            throw exceptions;
        }
    }
}
