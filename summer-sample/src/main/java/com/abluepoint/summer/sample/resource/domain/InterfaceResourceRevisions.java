package com.abluepoint.summer.sample.resource.domain;

import java.util.List;

/** Immutable, content-addressed catalog history; storage and serialization stay behind this port. */
public interface InterfaceResourceRevisions {
    List<InterfaceResourceCatalogView> all();
    InterfaceResourceCatalogView record(InterfaceResourceCatalog catalog);
    long count();
    long latestRevision();
    List<InterfaceResourceRevisionSummaryView> list(int page, int size);
    InterfaceResourceCatalogView read(long revision);
}
