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
    private final RequestIdAccessor requestId;

    DictionaryController(DictionaryService service, RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @GetMapping("/api/dictionaries")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.read')")
    List<DictionaryTypeView> list(Authentication authentication) { return service.list(authentication); }

    @PostMapping("/api/dictionaries")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.write')")
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
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.write')")
    DictionaryTypeView update(@PathVariable String code, @RequestBody DictionaryTypeRequest request, Authentication authentication) {
        return service.updateType(code, request, requestId.currentRequestId(), authentication);
    }

    @PostMapping("/api/dictionaries/{code}/items")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.write')")
    DictionaryItemView createItem(@PathVariable String code, @RequestBody DictionaryItemRequest request, Authentication authentication) {
        return service.createItem(code, request, requestId.currentRequestId(), authentication);
    }

    @PatchMapping("/api/dictionaries/{code}/items/{value}")
    @PreAuthorize("hasAuthority('SCOPE_platform.dictionary.write')")
    DictionaryItemView updateItem(@PathVariable String code, @PathVariable String value,
                                  @RequestBody DictionaryItemRequest request, Authentication authentication) {
        return service.updateItem(code, value, request, requestId.currentRequestId(), authentication);
    }

    @GetMapping("/public/dictionaries/{code}")
    @PreAuthorize("permitAll()")
    PublicDictionaryView publicRead(@PathVariable String code) { return service.publicRead(code); }
}
