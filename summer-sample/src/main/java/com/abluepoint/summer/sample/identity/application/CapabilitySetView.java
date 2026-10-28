package com.abluepoint.summer.sample.identity.application;

import java.util.List;

/**
 * The action-level capabilities available in one explicitly selected
 * workspace context. The revision is exposed as a stable conditional-read
 * version and is not an OAuth scope or a browser navigation structure.
 */
public record CapabilitySetView(
        String workspace,
        String enterpriseId,
        List<String> capabilities,
        String revision
) {

    public CapabilitySetView {
        capabilities = List.copyOf(capabilities);
    }
}
