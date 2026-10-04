package com.abluepoint.summer.security.autoconfigure.storage;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/** Default servlet HttpSession-backed security-context store. */
public final class DefaultSummerSessionStore extends HttpSessionSecurityContextRepository
        implements SummerSessionStore {
}
