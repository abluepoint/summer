package com.abluepoint.summer.sample.configuration;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.customer.CustomerAuditService;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class ConfigurationOverrideService {
    private static final String TENANT="ent-acme";
    private final ConfigurationOverrideRepository repository;
    private final DataScopeService scopes;
    private final CustomerAuditService audit;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final ConfigurationActionAccess actions;
    ConfigurationOverrideService(ConfigurationOverrideRepository repository, DataScopeService scopes, CustomerAuditService audit, ObjectMapper mapper, JdbcTemplate jdbc, ConfigurationActionAccess actions) {
        this.repository=repository; this.scopes=scopes; this.audit=audit; this.mapper=mapper; this.jdbc=jdbc; this.actions=actions;
    }
    public record Value(String valueJson,long version) {}

    @Transactional(readOnly=true)
    public Optional<Value> resolve(String resource,String key,Authentication auth) {
        var scope=scopes.filter(TENANT,resource,auth);
        String organization=DataScopeService.organizationId(auth);
        List<String> candidates=organization==null ? List.of("_tenant") : List.of(organization,"_tenant");
        for (String candidate:candidates) {
            // Presence determines precedence; the actual value is fetched only with a database scope predicate.
            if (!repository.existsByTenantIdAndResourceAndKeyAndScopeId(TENANT,resource,key,candidate)) continue;
            var row=repository.findOne(scope.<ConfigurationOverrideEntity>specification().and((root,q,b)->b.and(
                    b.equal(root.get("resource"),resource), b.equal(root.get("key"),key), b.equal(root.get("scopeId"),candidate))))
                    .filter(v->v.publicReadable && v.enabled).orElseThrow(ConfigurationOverrideService::hidden);
            return Optional.of(new Value(row.valueJson,row.version));
        }
        return Optional.empty();
    }

    @Transactional
    public ConfigurationOverrideView save(String resource,String key,String organization,ConfigurationOverrideRequest request,Authentication auth,String requestId) {
        if (auth==null || !auth.isAuthenticated()) throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        if (request==null || request.value()==null || organization==null || !organization.matches("[a-zA-Z0-9_-]{1,64}"))
            throw new SummerException(CommonErrorCode.INVALID_ARGUMENT);
        String org="_tenant".equals(organization)?null:organization;
        if (org!=null && jdbc.queryForObject("select count(*) from summer_organization_unit where enterprise_id=? and unit_id=? and status='ACTIVE'",Long.class,TENANT,org)==0)
            throw new SummerException(CommonErrorCode.INVALID_ARGUMENT);
        var scope=scopes.filter(TENANT,resource,auth);
        boolean existing=repository.existsByTenantIdAndResourceAndKeyAndScopeId(TENANT,resource,key,organization);
        actions.require(resource, existing ? "update" : "create", auth);
        actions.require(resource, request.enabled() != null && !request.enabled() ? "disable" : "publish", auth);
        ConfigurationOverrideEntity row;
        if (existing) {
            row=repository.findOne(scope.<ConfigurationOverrideEntity>specification().and((root,q,b)->b.and(b.equal(root.get("resource"),resource),b.equal(root.get("key"),key),b.equal(root.get("scopeId"),organization)))).orElseThrow(ConfigurationOverrideService::hidden);
            if (request.expectedVersion()==null || request.expectedVersion()!=row.version) throw new SummerException(CommonErrorCode.CONFLICT);
        } else {
            if (request.expectedVersion()!=null) throw new SummerException(CommonErrorCode.CONFLICT);
            scopes.assertWrite(TENANT,resource,auth,org);
            row=new ConfigurationOverrideEntity(); row.id=UUID.randomUUID().toString(); row.tenantId=TENANT; row.resource=resource; row.key=key;
            row.scopeId=organization; row.organizationId=org; row.createdBy=auth.getName(); row.ownerId=auth.getName();
        }
        row.valueJson=mapper.writeValueAsString(request.value()); row.publicReadable=Boolean.TRUE.equals(request.publicReadable());
        row.enabled=request.enabled()==null || request.enabled(); row.updatedAt=java.time.Instant.now(); repository.saveAndFlush(row);
        audit.recordResourceSuccess("configuration-override:"+resource+":"+key+":"+organization,"CONFIGURATION_OVERRIDE_PUBLISHED",requestId,auth);
        return new ConfigurationOverrideView(resource,key,org,row.publicReadable,row.enabled,row.version);
    }
    private static SummerException hidden() { return new SummerException(CommonErrorCode.NOT_FOUND); }
}
