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
    AuthUserController(AuthUserService service) {
        this.service = service;
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
