package com.abluepoint.summer.sample.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record AuthUserCreateRequest(@NotBlank @Size(max = 128) String username,
                         @NotBlank @Size(min = 12, max = 200) String password,
                         @Pattern(regexp = "ADMIN|USER") String role) {
    public AuthUserCreateRequest { if (role == null || role.isBlank()) role = "USER"; }
}
