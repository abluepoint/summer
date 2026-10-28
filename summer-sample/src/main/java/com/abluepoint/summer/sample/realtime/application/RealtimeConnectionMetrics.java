package com.abluepoint.summer.sample.realtime.application;
/** Aggregate operational view; it exposes no sessions or transport operations. */
@org.springframework.modulith.NamedInterface("connections")
public interface RealtimeConnectionMetrics { long activeConnectionCount(); }
