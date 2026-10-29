package com.abluepoint.summer.sample.realtime.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
public final class RealtimeMessageCursorException extends RuntimeException {
    public RealtimeMessageCursorException() { super("Message cursor must be a non-negative integer"); }
}
