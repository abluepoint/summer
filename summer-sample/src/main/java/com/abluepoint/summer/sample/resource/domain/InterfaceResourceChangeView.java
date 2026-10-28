package com.abluepoint.summer.sample.resource.domain;


public record InterfaceResourceChangeView(InterfaceResourceDescriptor before, InterfaceResourceDescriptor after, String kind) {}
