package com.abluepoint.summer.security.autoconfigure.storage;

import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Stable servlet security-context/session boundary.
 */
public interface SummerSessionStore extends SecurityContextRepository {
}
