package com.abluepoint.summer.sample.resource.domain;

import java.util.List;

public record InterfaceResourceSnapshot(List<InterfaceResourceDescriptor> items, List<InterfaceResourceDiscoveryIssue> issues) {}

