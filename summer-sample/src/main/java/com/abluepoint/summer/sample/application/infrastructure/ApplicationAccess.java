package com.abluepoint.summer.sample.application.infrastructure;

import com.abluepoint.summer.sample.application.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.*;

public final class ApplicationAccess {
    private ApplicationAccess() {}
    public static void requireRead(String e,Authentication a){ require(e,a,"enterprise.application.read","enterprise.applications.read"); }
    public static void requireWrite(String e,Authentication a){ require(e,a,"enterprise.application.write","enterprise.applications.write"); }
    private static void require(String e,Authentication a,String... caps){ if(!(a instanceof JwtAuthenticationToken jwt)||!ApplicationRules.permits(active(jwt,e),cap(jwt,e,caps),jwt.getAuthorities().stream().anyMatch(x->Arrays.stream(caps).anyMatch(c->("SCOPE_"+c).equals(x.getAuthority())))))throw new AccessDeniedException("Application capability denied"); }
    private static boolean active(JwtAuthenticationToken j,String e){ Object c=j.getToken().getClaims().get("enterprise_memberships"); return c instanceof Collection<?> v&&v.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(m->e.equals(String.valueOf(m.getOrDefault("enterpriseId",m.get("enterprise_id"))))&&"ACTIVE".equalsIgnoreCase(String.valueOf(m.getOrDefault("status",m.get("membership_status"))))); }
    private static boolean cap(JwtAuthenticationToken j,String e,String... caps){ Object c=j.getToken().getClaims().get("enterprise_capabilities"); if(!(c instanceof Map<?,?> m))return false; Object v=m.get(e); return v instanceof Collection<?> col&&col.stream().map(String::valueOf).anyMatch(s->Arrays.asList(caps).contains(s)); }
}
