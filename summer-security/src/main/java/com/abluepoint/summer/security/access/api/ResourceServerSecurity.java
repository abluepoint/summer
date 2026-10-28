package com.abluepoint.summer.security.access.api;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Explicit per-chain resource authentication; never participates in Spring's global Customizer lookup. */
@FunctionalInterface
public interface ResourceServerSecurity {
    void customize(HttpSecurity http);
}
