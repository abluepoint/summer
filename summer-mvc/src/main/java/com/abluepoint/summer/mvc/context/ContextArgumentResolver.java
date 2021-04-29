package com.abluepoint.summer.mvc.context;

import com.abluepoint.summer.common.exception.SummerRuntimeException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Map;

public class ContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().isAssignableFrom(Context.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Context context = (Context) webRequest.getAttribute(Context.CONTEXT_ATTR, WebRequest.SCOPE_REQUEST);
        if (context == null) {
            context = new DefaultContext();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) webRequest.getAttribute(Context.DATA_ATTR, WebRequest.SCOPE_REQUEST);
        if (data != null) {
            context.setData(data);
        } else {
            throw new SummerRuntimeException("system.config.error", new Object[] { webRequest.getContextPath() });
        }
        return context;
    }
}
