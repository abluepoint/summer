package com.abluepoint.summer.sample.resource.application;

import com.abluepoint.summer.sample.resource.domain.InterfaceResourceBindingPolicy;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalog;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalogView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDifferenceView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisionRules;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisionSummaryView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisions;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class InterfaceResourceHistory {
    private final InterfaceResourceRevisions revisions;
    private final InterfaceResourceBindingPolicy bindings;

    public InterfaceResourceHistory(InterfaceResourceRevisions revisions, InterfaceResourceBindingPolicy bindings) {
        this.revisions = revisions;
        this.bindings = bindings;
    }

    public InterfaceResourceCatalog protect(InterfaceResourceCatalog catalog) {
        return bindings.protect(catalog, revisions.all());
    }

    public boolean isReplaced(String permissionCode, String handler) { return bindings.isReplaced(permissionCode, handler); }

    public InterfaceResourceCatalogView record(InterfaceResourceCatalog catalog) {
        InterfaceResourceCatalogView recorded = revisions.record(catalog);
        bindings.remember(catalog);
        return recorded;
    }

    public long count() { return revisions.count(); }
    public long latestRevision() { return revisions.latestRevision(); }

    public List<InterfaceResourceRevisionSummaryView> list(int page, int size) {
        InterfaceResourceRevisionRules.requirePage(page, size);
        return revisions.list(page, size);
    }

    public InterfaceResourceCatalogView read(long revision) {
        InterfaceResourceRevisionRules.requireRevision(revision);
        return revisions.read(revision);
    }

    public InterfaceResourceDifferenceView diff(long from, long to) {
        return InterfaceResourceRevisionRules.difference(from, to, read(from), read(to));
    }
}
