package com.abluepoint.summer.sample.configuration;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

@Component
public class ConfigurationActionAccess {
    private final AuthorizationService authorization;
    ConfigurationActionAccess(AuthorizationService authorization) { this.authorization=authorization; }
    public void requirePublic(String resource, Authentication auth) {
        if (!(auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken)) return;
        String permission = "dictionaries".equals(resource) ? "platform.dictionary.read" : "system.parameter.read";
        if (!authorization.decision(auth, "ent-acme", permission, null).allowed())
            throw new AccessDeniedException("Configuration read denied");
    }
    public void require(String resource,String action,Authentication auth) {
        String prefix="dictionaries".equals(resource)?"platform.dictionary":"system.parameter";
        String permission=prefix+"."+action;
        boolean allowed=auth!=null && auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("SCOPE_"+permission) || a.getAuthority().equals("SCOPE_"+prefix+".write")
                || ("system-parameters".equals(resource) && (a.getAuthority().equals("SCOPE_platform.parameter."+action) || a.getAuthority().equals("SCOPE_platform.parameter.write"))));
        if (!allowed || !authorization.decision(auth,"ent-acme",permission,null).allowed())
            throw new AccessDeniedException("Configuration action denied");
    }
}
