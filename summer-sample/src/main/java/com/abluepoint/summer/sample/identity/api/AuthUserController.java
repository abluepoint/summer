package com.abluepoint.summer.sample.identity.api;

import com.abluepoint.summer.sample.identity.domain.AuthUserConflictException;
import com.abluepoint.summer.sample.identity.application.AuthUserService;
import com.abluepoint.summer.sample.identity.application.AuthUserView;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.abluepoint.summer.sample.identity.application.command.AuthUserActivationCommand;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyAuthority('SCOPE_enterprise.members.read','SCOPE_enterprise.members.write')")
public class AuthUserController {
    private final AuthUserService service;
    private final com.abluepoint.summer.mvc.request.api.RequestIdAccessor requestId;
    AuthUserController(AuthUserService service, com.abluepoint.summer.mvc.request.api.RequestIdAccessor requestId) {
        this.service = service;
        this.requestId = requestId;
    }

    @ExceptionHandler(AuthUserConflictException.class)
    @com.abluepoint.summer.mvc.response.api.RawResponse
    org.springframework.http.ResponseEntity<com.abluepoint.summer.common.response.api.ApiResponse<Void>> duplicate() {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                .header("X-Request-Id", requestId.currentRequestId())
                .body(com.abluepoint.summer.common.response.api.ApiResponse.failure(
                        new com.abluepoint.summer.common.response.api.ApiError("SUMMER_USER_CONFLICT", "Username already exists", List.of()),
                        requestId.currentRequestId()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.read')")
    List<AuthUserView> list() { return service.list(); }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    AuthUserView create(@Valid @RequestBody AuthUserCreateRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{username}")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    AuthUserView active(@PathVariable String username, @Valid @RequestBody AuthUserActivationRequest request) {
        return service.setActive(new AuthUserActivationCommand(username, request.active()));
    }



}
