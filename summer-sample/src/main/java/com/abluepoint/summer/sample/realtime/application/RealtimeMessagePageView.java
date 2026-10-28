package com.abluepoint.summer.sample.realtime.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public record RealtimeMessagePageView(List<RealtimeMessageView> items, long latestSequence, boolean hasMore) {
    public RealtimeMessagePageView {
        items = List.copyOf(items);
    }
}

