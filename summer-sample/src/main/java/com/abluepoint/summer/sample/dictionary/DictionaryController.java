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
    private final PublishedDictionaryReader published;
    private final RequestIdAccessor requestId;
    private final DictionaryNameResolver names;

    DictionaryController(DictionaryService service, DictionaryVersionService versions, PublishedDictionaryReader published, RequestIdAccessor requestId, DictionaryNameResolver names) {
        this.service = service;
        this.versions = versions;
        this.published = published;
        this.requestId = requestId;
        this.names = names;
    }

    @GetMapping({"/api/dictionaries", "/api/v1/platform/dictionaries"})
    @FieldContract
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    List<DictionaryTypeView> list(Authentication authentication) { return service.list(authentication); }

    @PostMapping({"/api/dictionaries/{code}/resolve-names", "/api/v1/platform/dictionaries/{code}/resolve-names"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    DictionaryNameResolution resolveNames(@PathVariable String code, @RequestBody DictionaryNameRequest request,
                                          Authentication authentication) {
        return names.resolve(code, request, authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/resolve-selection", "/api/v1/platform/dictionaries/{code}/resolve-selection"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    DictionaryNameResolution resolveSelection(@PathVariable String code, @RequestBody DictionaryNameRequest request,
                                              Authentication authentication) {
        return names.selection(code, request, authentication);
    }

    @GetMapping({"/api/dictionaries/{code}/effective", "/api/v1/platform/dictionaries/{code}/effective"})
    @Field(param = "code", style = "dictionaryCodeStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    EffectiveDictionaryView effective(@PathVariable String code, Authentication authentication) {
        return versions.effectiveRead(code, authentication);
    }

    @GetMapping({"/api/dictionaries/{code}/draft", "/api/v1/platform/dictionaries/{code}/draft"})
    @Field(param = "code", style = "dictionaryCodeStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    DictionaryDraftView draft(@PathVariable String code, Authentication auth) { return service.draft(code, auth); }

    @PutMapping({"/api/dictionaries/{code}/draft", "/api/v1/platform/dictionaries/{code}/draft"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    DictionaryDraftView saveDraft(@PathVariable String code, @RequestBody DictionaryDraftRequest request, Authentication auth) {
        return service.saveDraft(code, request, requestId.currentRequestId(), auth);
    }

    @PostMapping({"/api/dictionaries", "/api/v1/platform/dictionaries"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.create', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.create','SCOPE_platform.dictionary.write'))")
    @FieldContract
    @Field(param = "code", required = true, style = "dictionaryCodeStyle")
    @Field(param = "name", required = true, style = "dictionaryNameStyle")
    @Field(param="organizationId", required=false, style="systemParameterTenantStyle")
    @Field(param = "published", required = false, style = "dictionaryBooleanStyle")
    @Field(param = "publicReadable", required = false, style = "dictionaryBooleanStyle")
    @Field(param = "enabled", required = false, style = "dictionaryBooleanStyle")
    DictionaryTypeView create(@RequestBody DictionaryTypeRequest request, Authentication authentication) {
        return service.createType(request, requestId.currentRequestId(), authentication);
    }

    @PatchMapping({"/api/dictionaries/{code}", "/api/v1/platform/dictionaries/{code}"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.update', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.update','SCOPE_platform.dictionary.write'))")
    DictionaryTypeView update(@PathVariable String code, @RequestBody DictionaryTypeRequest request, Authentication authentication) {
        return service.updateType(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/items", "/api/v1/platform/dictionaries/{code}/items"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.create', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.create','SCOPE_platform.dictionary.write'))")
    DictionaryItemView createItem(@PathVariable String code, @RequestBody DictionaryItemRequest request, Authentication authentication) {
        return service.createItem(code, request, requestId.currentRequestId(), authentication);
    }

    @PatchMapping({"/api/dictionaries/{code}/items/{value}", "/api/v1/platform/dictionaries/{code}/items/{value}"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.update', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.update','SCOPE_platform.dictionary.write'))")
    DictionaryItemView updateItem(@PathVariable String code, @PathVariable String value,
                                  @RequestBody DictionaryItemRequest request, Authentication authentication) {
        return service.updateItem(code, value, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/publish", "/api/v1/platform/dictionaries/{code}/publish", "/api/dictionaries/{code}/versions/publish", "/api/v1/platform/dictionaries/{code}/versions/publish"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.publish', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.publish','SCOPE_platform.dictionary.write'))")
    DictionaryTypeVersionView publish(@PathVariable String code, @RequestBody DictionaryVersionRequest request,
                                      Authentication authentication) {
        return versions.publish(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/disable", "/api/v1/platform/dictionaries/{code}/disable", "/api/dictionaries/{code}/deactivate", "/api/v1/platform/dictionaries/{code}/deactivate", "/api/dictionaries/{code}/versions/disable", "/api/v1/platform/dictionaries/{code}/versions/disable", "/api/dictionaries/{code}/versions/deactivate", "/api/v1/platform/dictionaries/{code}/versions/deactivate"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.disable', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.disable','SCOPE_platform.dictionary.write'))")
    DictionaryTypeVersionView disable(@PathVariable String code, @RequestBody DictionaryVersionRequest request,
                                      Authentication authentication) {
        return versions.disable(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping({"/api/dictionaries/{code}/rollback", "/api/v1/platform/dictionaries/{code}/rollback", "/api/dictionaries/{code}/versions/rollback", "/api/v1/platform/dictionaries/{code}/versions/rollback"})

    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.rollback', 'platform.dictionary.write') or (hasAnyAuthority('SCOPE_platform.dictionary.rollback','SCOPE_platform.dictionary.write'))")
    DictionaryTypeVersionView rollback(@PathVariable String code, @RequestBody DictionaryVersionRequest request,
                                       Authentication authentication) {
        return versions.rollback(code, request, requestId.currentRequestId(), authentication);
    }

    @GetMapping({"/api/dictionaries/{code}/versions", "/api/v1/platform/dictionaries/{code}/versions"})
    @Field(param = "code", style = "dictionaryCodeStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    List<DictionaryVersionView> versions(@PathVariable String code, Authentication authentication) {
        return versions.list(code, authentication);
    }

    @GetMapping({"/api/dictionaries/{code}/versions/{version}", "/api/v1/platform/dictionaries/{code}/versions/{version}"})
    @Field(param = "code", style = "dictionaryCodeStyle")
    @Field(param = "version", style = "positiveRevisionStyle")
    @PreAuthorize("@businessMethodAccess.require(authentication, 'platform.dictionary.read') or (hasAuthority('SCOPE_platform.dictionary.read'))")
    DictionaryHistoryView history(@PathVariable String code, @PathVariable long version, Authentication authentication) {
        return versions.history(code, version, authentication);
    }

    @GetMapping({"/public/dictionaries/{code}", "/public/enterprises/{enterpriseId}/dictionaries/{code}"})
    @PreAuthorize("permitAll()")
    PublicDictionaryView publicRead(@PathVariable String code, Authentication authentication) { return versions.publicRead(code, authentication); }

    @GetMapping({"/public/dictionaries/{code}/items/{value}", "/public/enterprises/{enterpriseId}/dictionaries/{code}/items/{value}"})
    @PreAuthorize("permitAll()")
    DictionaryItemView publicItem(@PathVariable String code, @PathVariable String value, Authentication authentication) {
        return published.readItem(code, value, authentication);
    }
}
