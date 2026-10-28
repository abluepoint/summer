package com.abluepoint.summer.security.identity.spi;

import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Stable identity lookup boundary used by Summer Security's authorization-server
 * defaults. Applications may provide their own implementation when the
 * {@code summer.security.storage.identity} selection names a non-memory adapter.
 */
public interface SummerIdentityStore extends UserDetailsService {
}
