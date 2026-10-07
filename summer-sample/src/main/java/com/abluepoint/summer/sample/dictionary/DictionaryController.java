package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldContract;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
class DictionaryController {
    private final DictionaryService service;
    private final DictionaryVersionService versions;
    private final RequestIdAccessor requestId;

    DictionaryController(DictionaryService service, DictionaryVersionService versions, RequestIdAccessor requestId) {
        this.service = service;
        this.versions = versions;
        this.requestId = requestId;
    }

    @GetMapping("/api/dictionaries")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.read")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.read')")
    List<DictionaryTypeView> list(Authentication authentication) { return service.list(authentication); }

    @PostMapping("/api/dictionaries")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.create")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.create','SCOPE_platform.dictionary.write')")
    @FieldContract
    @Field(param = "code", required = true, style = "dictionaryCodeStyle")
    @Field(param = "name", required = true, style = "dictionaryNameStyle")
    @Field(param = "published", required = false, style = "dictionaryBooleanStyle")
    @Field(param = "publicReadable", required = false, style = "dictionaryBooleanStyle")
    @Field(param = "enabled", required = false, style = "dictionaryBooleanStyle")
    DictionaryTypeView create(@RequestBody DictionaryTypeRequest request, Authentication authentication) {
        return service.createType(request, requestId.currentRequestId(), authentication);
    }

    @PatchMapping("/api/dictionaries/{code}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.update")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.update','SCOPE_platform.dictionary.write')")
    DictionaryTypeView update(@PathVariable String code, @RequestBody DictionaryTypeRequest request, Authentication authentication) {
        return service.updateType(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping("/api/dictionaries/{code}/items")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.create")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.create','SCOPE_platform.dictionary.write')")
    DictionaryItemView createItem(@PathVariable String code, @RequestBody DictionaryItemRequest request, Authentication authentication) {
        return service.createItem(code, request, requestId.currentRequestId(), authentication);
    }

    @PatchMapping("/api/dictionaries/{code}/items/{value}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.update")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.update','SCOPE_platform.dictionary.write')")
    DictionaryItemView updateItem(@PathVariable String code, @PathVariable String value,
                                  @RequestBody DictionaryItemRequest request, Authentication authentication) {
        return service.updateItem(code, value, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/publish", "/api/dictionaries/{code}/versions/publish"})
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.publish','SCOPE_platform.dictionary.write')")
    DictionaryTypeVersionView publish(@PathVariable String code, @RequestBody DictionaryVersionRequest request,
                                      Authentication authentication) {
        return versions.publish(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/disable", "/api/dictionaries/{code}/deactivate", "/api/dictionaries/{code}/versions/disable", "/api/dictionaries/{code}/versions/deactivate"})
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.disable")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.disable','SCOPE_platform.dictionary.write')")
    DictionaryTypeVersionView disable(@PathVariable String code, @RequestBody DictionaryVersionRequest request,
                                      Authentication authentication) {
        return versions.disable(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/rollback", "/api/dictionaries/{code}/versions/rollback"})
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.rollback")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.rollback','SCOPE_platform.dictionary.write')")
    DictionaryTypeVersionView rollback(@PathVariable String code, @RequestBody DictionaryVersionRequest request,
                                       Authentication authentication) {
        return versions.rollback(code, request, requestId.currentRequestId(), authentication);
    }

    @GetMapping("/api/dictionaries/{code}/versions")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.read")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.read')")
    List<DictionaryVersionView> versions(@PathVariable String code, Authentication authentication) {
        return versions.list(code, authentication);
    }

    @PutMapping("/api/dictionaries/{code}/overrides/{organization}")
    @com.abluepoint.summer.sample.resource.ResourcePermission("platform.dictionary.publish")
    @PreAuthorize("hasAnyAuthority('SCOPE_platform.dictionary.publish','SCOPE_platform.dictionary.write')")
    com.abluepoint.summer.sample.configuration.ConfigurationOverrideView override(@PathVariable String code, @PathVariable String organization,
            @RequestBody com.abluepoint.summer.sample.configuration.ConfigurationOverrideRequest request, Authentication auth) {
        return versions.override(code, organization, request, auth, requestId.currentRequestId());
    }

    @GetMapping("/public/dictionaries/{code}")
    @PreAuthorize("permitAll()")
    PublicDictionaryView publicRead(@PathVariable String code, Authentication authentication) { return versions.publicRead(code, authentication); }
}
