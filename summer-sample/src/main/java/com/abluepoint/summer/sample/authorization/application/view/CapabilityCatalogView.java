package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record CapabilityCatalogView(String workspace, String enterpriseId, List<CapabilityView> capabilities, long version) {}
