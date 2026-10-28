package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record BundleView(String bundleId, String enterpriseId, String name, String code, String status,
                  List<String> capabilityIds, long version, String changeSummary) {}
