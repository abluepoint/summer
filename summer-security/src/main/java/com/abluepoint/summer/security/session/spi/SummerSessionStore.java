package com.abluepoint.summer.security.session.spi;

import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Stable servlet security-context/session boundary.
 */
public interface SummerSessionStore extends SecurityContextRepository {
}
