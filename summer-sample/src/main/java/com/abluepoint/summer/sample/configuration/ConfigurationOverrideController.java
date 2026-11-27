package com.abluepoint.summer.sample.configuration;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
class ConfigurationOverrideController {
    private final ConfigurationOverrideService service;
    private final RequestIdAccessor requestId;
    ConfigurationOverrideController(ConfigurationOverrideService service,RequestIdAccessor requestId) {this.service=service;this.requestId=requestId;}
    @Field(param = "key", style = "systemParameterKeyStyle")
    @Field(param = "organization", style = "requestTextStyle")
    @Field(param = "enterpriseId", required = false, style = "requestTextStyle")
    @GetMapping({"/api/system-parameters/{key}/overrides/{organization}/versions","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/versions"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.read') or (hasAuthority('SCOPE_system.parameter.read'))")
    List<ConfigurationOverrideRevisionView> parameterVersions(@PathVariable String key,@PathVariable String organization,Authentication auth) {
        return service.history("system-parameters",key,organization,auth);
    }
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/draft","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/draft"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.update', 'system.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_system.parameter.write'))")
    ConfigurationOverrideView parameterDraft(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideRequest request,Authentication auth) {
        return service.draft("system-parameters",key,organization,request,auth,requestId.currentRequestId());
    }
    @Field(param = "key", style = "systemParameterKeyStyle")
    @Field(param = "organization", style = "requestTextStyle")
    @Field(param = "enterpriseId", required = false, style = "requestTextStyle")
    @Field(param = "revision", required = false, nullable = true, style = "requestLongStyle")
    @Field(param = "expectedVersion", required = false, nullable = true, style = "requestLongStyle")
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/publish","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/publish"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.publish', 'system.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_system.parameter.write'))")
    ConfigurationOverrideView parameterPublish(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("system-parameters",key,organization,"publish",request,auth,requestId.currentRequestId());
    }
    @Field(param = "key", style = "systemParameterKeyStyle")
    @Field(param = "organization", style = "requestTextStyle")
    @Field(param = "enterpriseId", required = false, style = "requestTextStyle")
    @Field(param = "revision", required = false, nullable = true, style = "requestLongStyle")
    @Field(param = "expectedVersion", required = false, nullable = true, style = "requestLongStyle")
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/disable","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/disable"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.disable', 'system.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.disable','SCOPE_system.parameter.write'))")
    ConfigurationOverrideView parameterDisable(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("system-parameters",key,organization,"disable",request,auth,requestId.currentRequestId());
    }
    @Field(param = "key", style = "systemParameterKeyStyle")
    @Field(param = "organization", style = "requestTextStyle")
    @Field(param = "enterpriseId", required = false, style = "requestTextStyle")
    @Field(param = "revision", required = false, nullable = true, style = "requestLongStyle")
    @Field(param = "expectedVersion", required = false, nullable = true, style = "requestLongStyle")
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/rollback","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/rollback"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'system.parameter.rollback', 'system.parameter.write') or (hasAnyAuthority('SCOPE_system.parameter.rollback','SCOPE_system.parameter.write'))")
    ConfigurationOverrideView parameterRollback(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("system-parameters",key,organization,"rollback",request,auth,requestId.currentRequestId());
    }
}
