package com.abluepoint.summer.sample.resource.domain;

import java.util.List;

public record InterfaceResourceDifferenceView(long from, long to, List<InterfaceResourceDescriptor> added,
                                   List<InterfaceResourceDescriptor> removed, List<InterfaceResourceChangeView> changed) {}
