package com.abluepoint.summer.sample.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record RealtimeConnectionView(String enterpriseId, int connections, int maxConnections, String status) {}
