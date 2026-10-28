package com.abluepoint.summer.sample.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyAuthority('SCOPE_enterprise.members.read','SCOPE_enterprise.members.write')")
class AuthUserController {
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
    List<AuthUserService.AuthUserView> list() { return service.list(); }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    AuthUserService.AuthUserView create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request.username(), request.password(), request.role());
    }

    @PatchMapping("/{username}")
    @PreAuthorize("hasAuthority('SCOPE_enterprise.members.write')")
    AuthUserService.AuthUserView active(@PathVariable String username, @Valid @RequestBody ActiveUserRequest request) {
        return service.setActive(username, request.active());
    }

    record CreateUserRequest(@NotBlank @Size(max = 128) String username,
                             @NotBlank @Size(min = 12, max = 200) String password,
                             @Pattern(regexp = "ADMIN|USER") String role) {
        CreateUserRequest { if (role == null || role.isBlank()) role = "USER"; }
    }
    record ActiveUserRequest(boolean active) {}
}
