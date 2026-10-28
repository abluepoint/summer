package com.abluepoint.summer.sample.resource.domain;

/** Discovers metadata without executing business handlers. */
public interface InterfaceResourceDiscovery {
    InterfaceResourceCatalog discover();
}
