package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Retired target adapter. Historical capture executes the separately pinned old bridge. */
@Service
public class LegacyResourceAdapter {
    LegacyResourceAdapter(ObjectProvider<RequestMappingHandlerMapping> mappings,JdbcTemplate jdbc) {}
    public Resource resolve(HttpServletRequest request,HandlerMethod handler) { throw retired(); }
    public Resource register(String application,String source,String method,String path,
            Map<String,List<String>> conditions,String oldCode) { throw retired(); }
    String permission(Resource resource) { return null; }
    private static AccessDeniedException retired() {
        return new AccessDeniedException("Legacy code resource bindings are retired; use managed resources");
    }
}
