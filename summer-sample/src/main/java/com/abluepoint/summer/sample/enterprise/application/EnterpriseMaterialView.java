package com.abluepoint.summer.sample.enterprise.application;

import java.util.List;

public record EnterpriseMaterialView(String name, String mediaType, long size) {
    // Existing revision hashes use the historical record rendering.
    @Override public String toString() { return "MaterialMetadata[" + "name=" + name + ", " + "mediaType=" + mediaType + ", " + "size=" + size + "]"; }
}
