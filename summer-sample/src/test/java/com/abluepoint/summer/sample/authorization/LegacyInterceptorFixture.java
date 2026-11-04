package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.resource.ResourcePermission;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import static org.mockito.Mockito.*;

/** Retains legacy unit fixtures while the real HTTP tests exercise the assembled adapters. */
final class LegacyInterceptorFixture {
    static AuthorizationRequestInterceptor create(AuthorizationService legacy, RequestIdAccessor requestId) {
        var resources = mock(LegacyResourceAdapter.class);
        when(resources.resolve(any(), any())).thenAnswer(invocation -> {
            jakarta.servlet.http.HttpServletRequest request = invocation.getArgument(0);
            org.springframework.web.method.HandlerMethod handler = invocation.getArgument(1);
            var annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), ResourcePermission.class);
            if (annotation == null) annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), ResourcePermission.class);
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            String path = pattern instanceof PathPattern p ? p.getPatternString() : String.valueOf(pattern);
            return new ResourceAuthorization.Resource(annotation == null ? request.getMethod() + ":" + path : annotation.value(), "INTERFACE", "MVC");
        });
        var unified = mock(UnifiedAuthorizationService.class);
        when(unified.decide(any())).thenAnswer(invocation -> {
            ResourceAuthorization.Request request = invocation.getArgument(0);
            var result = legacy.decision(request.principal(), request.domain().id(), request.resource().id(), request.requestId());
            return new ResourceAuthorization.Decision(result.allowed(), result.reasonCode(), 0, request.domain(), request.resource());
        });
        return new AuthorizationRequestInterceptor(legacy, requestId, unified, resources);
    }
}
