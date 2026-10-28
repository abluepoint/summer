package com.abluepoint.summer.sample.realtime.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public final class RealtimeMessageNotFoundException extends RuntimeException {
    public RealtimeMessageNotFoundException() { super("Message was not found"); }
}
