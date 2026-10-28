package com.abluepoint.summer.sample.configuration;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.error.api.SummerException;
import com.abluepoint.summer.sample.authorization.DataScopeService;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.customer.service.CustomerAuditService;
import com.abluepoint.summer.sample.cache.VersionedTwoLevelCache;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

@Service
public class ConfigurationOverrideService {
    private final ConfigurationOverrideRepository repository;
    private final ConfigurationOverrideRevisionRepository revisions;
    private final DataScopeService scopes;
    private final CustomerAuditService audit;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;
    private final ConfigurationActionAccess actions;
    private final List<ConfigurationValueValidator> validators;
    private final AuthorizationChangeEventService events;
    private final VersionedTwoLevelCache cache;
    ConfigurationOverrideService(ConfigurationOverrideRepository repository, ConfigurationOverrideRevisionRepository revisions,
            DataScopeService scopes, CustomerAuditService audit, ObjectMapper mapper, JdbcTemplate jdbc,
            ConfigurationActionAccess actions, List<ConfigurationValueValidator> validators,
            AuthorizationChangeEventService events, VersionedTwoLevelCache cache) {
        this.repository=repository; this.revisions=revisions; this.scopes=scopes; this.audit=audit; this.mapper=mapper;
        this.jdbc=jdbc; this.actions=actions; this.validators=validators; this.events=events; this.cache=cache;
    }
    public record Value(String valueJson,long version) {}

    @Transactional(readOnly=true)
    public Optional<Value> resolve(String resource,String key,Authentication auth) { return resolve(ConfigurationTenantContext.resolve(auth),resource,key,auth); }

    @Transactional(readOnly=true)
    public Optional<Value> resolve(String tenant,String resource,String key,Authentication auth) {
        var scope=scopes.filter(tenant,resource,auth);
        String organization=scopes.organization(tenant,auth);
        List<String> candidates=organization==null ? List.of("_tenant") : List.of(organization,"_tenant");
        for (String candidate:candidates) {
            // Draft-only overrides do not participate in precedence. A hidden published override blocks fallback.
            if (!repository.existsByTenantIdAndResourceAndKeyAndScopeIdAndPublishedRevisionIsNotNull(tenant,resource,key,candidate)) continue;
            var row=repository.findOne(scope.<ConfigurationOverrideEntity>specification().and((root,q,b)->b.and(
                    b.equal(root.get("resource"),resource), b.equal(root.get("key"),key), b.equal(root.get("scopeId"),candidate))))
                    .filter(v->v.publicReadable && v.enabled).orElseThrow(ConfigurationOverrideService::hidden);
            return Optional.of(new Value(row.valueJson,row.version));
        }
        return Optional.empty();
    }

    @Transactional
    public ConfigurationOverrideView save(String resource,String key,String organization,ConfigurationOverrideRequest request,Authentication auth,String requestId) {
        return saveValue(resource,key,organization,request,auth,requestId,false);
    }

    @Transactional
    public ConfigurationOverrideView draft(String resource,String key,String organization,ConfigurationOverrideRequest request,Authentication auth,String requestId) {
        return saveValue(resource,key,organization,request,auth,requestId,true);
    }

    private ConfigurationOverrideView saveValue(String resource,String key,String organization,ConfigurationOverrideRequest request,Authentication auth,String requestId,boolean draft) {
        String tenant=ConfigurationTenantContext.resolve(auth);
        ConfigurationTenantContext.requireMembership(tenant,auth);
        if(request==null||request.value()==null||organization==null||!organization.matches("[a-zA-Z0-9_-]{1,64}")) throw invalid();
        validator(resource).validate(tenant,key,request.value(),auth);
        String org="_tenant".equals(organization)?null:organization;
        scopes.assertOrganization(tenant,org);
        boolean existing=repository.existsByTenantIdAndResourceAndKeyAndScopeId(tenant,resource,key,organization);
        actions.require(resource,existing?"update":"create",auth);
        if(!draft) actions.require(resource,Boolean.FALSE.equals(request.enabled())?"disable":"publish",auth);
        ConfigurationOverrideEntity row;
        if(existing) { row=current(tenant,resource,key,organization,auth); expected(row,request.expectedVersion()); }
        else {
            if(request.expectedVersion()!=null) throw conflict();
            scopes.assertWrite(tenant,resource,auth,org);
            row=new ConfigurationOverrideEntity();row.id=UUID.randomUUID().toString();row.tenantId=tenant;
            row.resource=resource;row.key=key;row.scopeId=organization;row.organizationId=org;row.createdBy=auth.getName();row.ownerId=auth.getName();
            row.valueJson="null";
        }
        String json=mapper.writeValueAsString(request.value());
        boolean publicReadable=Boolean.TRUE.equals(request.publicReadable()), enabled=!Boolean.FALSE.equals(request.enabled());
        String state=draft?"DRAFT":enabled?"PUBLISHED":"DISABLED";
        var revision=append(row,state,json,publicReadable,enabled,auth,requestId);
        if(!draft) { row.valueJson=json;row.publicReadable=publicReadable;row.enabled=enabled;row.publishedRevision=row.latestRevision; }
        persist(row,revision);
        evidence(row,state,requestId,auth);
        return view(row,state);
    }

    @Transactional(readOnly=true)
    public List<ConfigurationOverrideRevisionView> history(String resource,String key,String organization,Authentication auth) {
        String tenant=ConfigurationTenantContext.resolve(auth);actions.require(resource,"read",auth);
        var row=current(tenant,resource,key,organization,auth);
        return revisions.findByTenantIdAndOverrideIdOrderByRevisionDesc(tenant,row.id).stream().map(r->
                new ConfigurationOverrideRevisionView(r.revision,r.state,mapper.readValue(r.valueJson,Object.class),r.publicReadable,r.enabled,r.principal,r.createdAt)).toList();
    }

    @Transactional
    public ConfigurationOverrideView transition(String resource,String key,String organization,String action,ConfigurationOverrideCommand command,Authentication auth,String requestId) {
        if(!Set.of("publish","disable","rollback").contains(action)) throw invalid();
        String tenant=ConfigurationTenantContext.resolve(auth);actions.require(resource,action,auth);
        var row=current(tenant,resource,key,organization,auth);
        expected(row,command==null?null:command.expectedVersion());
        String json=row.valueJson;boolean publicReadable=row.publicReadable,enabled=row.enabled;
        if("disable".equals(action)) {
            if(row.publishedRevision==null) throw conflict();
            enabled=false;
        } else {
            if(command.revision()==null) throw invalid();
            var target=revisions.findByTenantIdAndOverrideIdAndRevision(tenant,row.id,command.revision()).orElseThrow(ConfigurationOverrideService::hidden);
            if("publish".equals(action) ? !"DRAFT".equals(target.state) : "DRAFT".equals(target.state)) throw conflict();
            json=target.valueJson;publicReadable=target.publicReadable;enabled=target.enabled;
            if("publish".equals(action)&&!enabled) throw invalid();
            validator(resource).validate(tenant,key,mapper.readValue(json,Object.class),auth);
        }
        String state=switch(action){case "publish"->"PUBLISHED";case "disable"->"DISABLED";default->"ROLLED_BACK";};
        var revision=append(row,state,json,publicReadable,enabled,auth,requestId);
        row.valueJson=json;row.publicReadable=publicReadable;row.enabled=enabled;row.publishedRevision=row.latestRevision;
        persist(row,revision);evidence(row,state,requestId,auth);return view(row,state);
    }

    private ConfigurationOverrideEntity current(String tenant,String resource,String key,String organization,Authentication auth) {
        validator(resource).requireBase(tenant,key,auth);
        return repository.findOne(scopes.filter(tenant,resource,auth).<ConfigurationOverrideEntity>specification().and((r,q,b)->b.and(
                b.equal(r.get("resource"),resource),b.equal(r.get("key"),key),b.equal(r.get("scopeId"),organization)))).orElseThrow(ConfigurationOverrideService::hidden);
    }
    private ConfigurationOverrideRevisionEntity append(ConfigurationOverrideEntity row,String state,String json,boolean pub,boolean enabled,Authentication auth,String requestId) {
        row.latestRevision++;row.updatedAt=java.time.Instant.now();
        var revision=new ConfigurationOverrideRevisionEntity();revision.id=UUID.randomUUID().toString();revision.overrideId=row.id;
        revision.tenantId=row.tenantId;revision.revision=row.latestRevision;revision.state=state;revision.valueJson=json;
        revision.publicReadable=pub;revision.enabled=enabled;revision.principal=auth.getName();revision.requestId=requestId;revision.createdAt=row.updatedAt;
        return revision;
    }
    private void persist(ConfigurationOverrideEntity row, ConfigurationOverrideRevisionEntity revision) {
        try { repository.saveAndFlush(row);revisions.saveAndFlush(revision); }
        catch(org.springframework.dao.OptimisticLockingFailureException | org.springframework.dao.DataIntegrityViolationException conflict) { throw conflict(); }
    }
    private void evidence(ConfigurationOverrideEntity row,String state,String requestId,Authentication auth) {
        String action="CONFIGURATION_OVERRIDE_"+state;
        audit.recordTenantResourceSuccess(row.tenantId,"configuration-override:"+row.resource+":"+row.key+":"+row.scopeId,action,requestId,auth);
        events.enqueue(row.tenantId,"CONFIGURATION_OVERRIDE",row.id,action,row.latestRevision,requestId);
        cache.invalidate(row.tenantId,"dictionaries".equals(row.resource)?"dictionary":"system-parameter");
    }
    private ConfigurationValueValidator validator(String resource) { return validators.stream().filter(v->v.resource().equals(resource)).findFirst().orElseThrow(ConfigurationOverrideService::invalid); }
    private ConfigurationOverrideView view(ConfigurationOverrideEntity r,String state) { return new ConfigurationOverrideView(r.resource,r.key,r.organizationId,r.publicReadable,r.enabled,r.version,r.latestRevision,r.publishedRevision,state); }
    private static void expected(ConfigurationOverrideEntity row,Long version) { if(version==null||version!=row.version) throw conflict(); }
    private static SummerException hidden() { return new SummerException(CommonErrorCode.NOT_FOUND); }
    private static SummerException invalid() { return new SummerException(CommonErrorCode.INVALID_ARGUMENT); }
    private static SummerException conflict() { return new SummerException(CommonErrorCode.CONFLICT); }
}
