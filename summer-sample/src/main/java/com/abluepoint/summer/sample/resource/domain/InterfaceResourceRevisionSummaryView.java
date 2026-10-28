package com.abluepoint.summer.sample.resource.domain;


public record InterfaceResourceRevisionSummaryView(long revision, String fingerprint, int resourceCount, String createdAt) {}
