package com.abluepoint.summer.sample.resource.application;

import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalog;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalogView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDiscovery;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisionRules;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public final class InterfaceResourceCatalogService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(InterfaceResourceCatalogService.class);
    private final InterfaceResourceDiscovery discovery;
    private final InterfaceResourceHistory history;
    private volatile InterfaceResourceCatalogView current = new InterfaceResourceCatalogView(List.of(), 0);
    private volatile InterfaceResourceCatalog catalog = new InterfaceResourceCatalog(List.of());

    public InterfaceResourceCatalogService(InterfaceResourceDiscovery discovery, InterfaceResourceHistory history) {
        this.discovery = discovery;
        this.history = history;
    }

    public synchronized void refresh() {
        long previous = current.revision() == 0 ? history.latestRevision() : current.revision();
        catalog = history.protect(discovery.discover());
        current = history.record(catalog);
        if (previous != current.revision()) {
            var difference = previous == 0 ? null : history.diff(previous, current.revision());
            log.info("Interface resource revision={} added={} removed={} changed={} complete={}", current.revision(),
                    difference == null ? current.items().size() : difference.added().size(),
                    difference == null ? 0 : difference.removed().size(), difference == null ? 0 : difference.changed().size(), current.complete());
        }
    }

    public InterfaceResourceCatalogView read(Authentication authentication) {
        requirePlatformRead(authentication);
        refresh();
        return current;
    }

    public InterfaceResourceCatalogPageView readPage(Authentication authentication, int page, int size, Long revision, boolean rescan) {
        requirePlatformRead(authentication);
        InterfaceResourceCatalogPageView.validate(page, size);
        InterfaceResourceRevisionRules.validateSelection(revision, rescan);
        // Lifecycle events keep this immutable snapshot current. Paging must not
        // rediscover mappings or deserialize every stored revision on each click.
        InterfaceResourceCatalogView view = rescan ? read(authentication) : current;
        if (revision != null && revision != view.revision()) view = history.read(revision);
        return InterfaceResourceCatalogPageView.of(view, page, size);
    }

    public InterfaceResourceCatalog snapshot() {
        return catalog;
    }

    public static void requirePlatformRead(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !hasClaim(jwt, "platform.interface-resources.read")
                || jwt.getAuthorities().stream().noneMatch(
                authority -> "SCOPE_platform.interface-resources.read".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Interface resource catalog denied");
        }
    }

    private static boolean hasClaim(JwtAuthenticationToken jwt, String capability) {
        Object claims = jwt.getToken().getClaims().get("platform_capabilities");
        return claims instanceof Collection<?> values && values.stream()
                .map(String::valueOf)
                .anyMatch(capability::equals);
    }
}
