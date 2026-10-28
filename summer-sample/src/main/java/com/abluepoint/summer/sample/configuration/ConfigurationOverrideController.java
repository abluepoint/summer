package com.abluepoint.summer.sample.configuration;
import com.abluepoint.summer.sample.resource.api.ResourcePermission;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
class ConfigurationOverrideController {
    private final ConfigurationOverrideService service;
    private final RequestIdAccessor requestId;
    ConfigurationOverrideController(ConfigurationOverrideService service,RequestIdAccessor requestId) {this.service=service;this.requestId=requestId;}
    @GetMapping({"/api/system-parameters/{key}/overrides/{organization}/versions","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/versions"})
    @ResourcePermission("system.parameter.read")
    @PreAuthorize("hasAuthority('SCOPE_system.parameter.read')")
    List<ConfigurationOverrideRevisionView> parameterVersions(@PathVariable String key,@PathVariable String organization,Authentication auth) {
        return service.history("system-parameters",key,organization,auth);
    }
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/draft","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/draft"})
    @ResourcePermission("system.parameter.update")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.update','SCOPE_system.parameter.write')")
    ConfigurationOverrideView parameterDraft(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideRequest request,Authentication auth) {
        return service.draft("system-parameters",key,organization,request,auth,requestId.currentRequestId());
    }
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/publish","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/publish"})
    @ResourcePermission("system.parameter.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.publish','SCOPE_system.parameter.write')")
    ConfigurationOverrideView parameterPublish(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("system-parameters",key,organization,"publish",request,auth,requestId.currentRequestId());
    }
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/disable","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/disable"})
    @ResourcePermission("system.parameter.disable")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.disable','SCOPE_system.parameter.write')")
    ConfigurationOverrideView parameterDisable(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("system-parameters",key,organization,"disable",request,auth,requestId.currentRequestId());
    }
    @PostMapping({"/api/system-parameters/{key}/overrides/{organization}/rollback","/api/v1/enterprises/{enterpriseId}/system-parameters/{key}/overrides/{organization}/rollback"})
    @ResourcePermission("system.parameter.rollback")
    @PreAuthorize("hasAnyAuthority('SCOPE_system.parameter.rollback','SCOPE_system.parameter.write')")
    ConfigurationOverrideView parameterRollback(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("system-parameters",key,organization,"rollback",request,auth,requestId.currentRequestId());
    }
    @GetMapping({"/api/dictionaries/{key}/overrides/{organization}/versions","/api/v1/enterprises/{enterpriseId}/dictionaries/{key}/overrides/{organization}/versions"})
    @ResourcePermission("platform.dictionary.read")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.read')")
    List<ConfigurationOverrideRevisionView> dictionaryVersions(@PathVariable String key,@PathVariable String organization,Authentication auth) {
        return service.history("dictionaries",key,organization,auth);
    }
    @PostMapping({"/api/dictionaries/{key}/overrides/{organization}/draft","/api/v1/enterprises/{enterpriseId}/dictionaries/{key}/overrides/{organization}/draft"})
    @ResourcePermission("platform.dictionary.update")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.update','SCOPE_platform.dictionary.write')")
    ConfigurationOverrideView dictionaryDraft(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideRequest request,Authentication auth) {
        return service.draft("dictionaries",key,organization,request,auth,requestId.currentRequestId());
    }
    @PostMapping({"/api/dictionaries/{key}/overrides/{organization}/publish","/api/v1/enterprises/{enterpriseId}/dictionaries/{key}/overrides/{organization}/publish"})
    @ResourcePermission("platform.dictionary.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.publish','SCOPE_platform.dictionary.write')")
    ConfigurationOverrideView dictionaryPublish(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("dictionaries",key,organization,"publish",request,auth,requestId.currentRequestId());
    }
    @PostMapping({"/api/dictionaries/{key}/overrides/{organization}/disable","/api/v1/enterprises/{enterpriseId}/dictionaries/{key}/overrides/{organization}/disable"})
    @ResourcePermission("platform.dictionary.disable")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.disable','SCOPE_platform.dictionary.write')")
    ConfigurationOverrideView dictionaryDisable(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("dictionaries",key,organization,"disable",request,auth,requestId.currentRequestId());
    }
    @PostMapping({"/api/dictionaries/{key}/overrides/{organization}/rollback","/api/v1/enterprises/{enterpriseId}/dictionaries/{key}/overrides/{organization}/rollback"})
    @ResourcePermission("platform.dictionary.rollback")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.rollback','SCOPE_platform.dictionary.write')")
    ConfigurationOverrideView dictionaryRollback(@PathVariable String key,@PathVariable String organization,@RequestBody ConfigurationOverrideCommand request,Authentication auth) {
        return service.transition("dictionaries",key,organization,"rollback",request,auth,requestId.currentRequestId());
    }
}
