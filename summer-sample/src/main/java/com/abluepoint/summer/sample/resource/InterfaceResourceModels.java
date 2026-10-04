package com.abluepoint.summer.sample.resource;

import java.util.List;

record InterfaceResourceCatalogView(List<InterfaceResourceDescriptor> items, long revision) {

    InterfaceResourceCatalogView {
        items = List.copyOf(items);
    }
}
