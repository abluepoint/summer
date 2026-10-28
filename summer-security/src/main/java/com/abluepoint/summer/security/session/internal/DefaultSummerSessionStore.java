package com.abluepoint.summer.security.session.internal;

import com.abluepoint.summer.security.session.spi.SummerSessionStore;

import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/** Default servlet HttpSession-backed security-context store. */
public final class DefaultSummerSessionStore extends HttpSessionSecurityContextRepository
        implements SummerSessionStore {
}
