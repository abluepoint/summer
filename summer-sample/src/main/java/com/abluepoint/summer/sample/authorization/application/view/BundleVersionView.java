package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record BundleVersionView(String bundleId, long version, List<String> capabilityIds, String changeSummary) {}
