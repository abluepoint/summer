package com.abluepoint.summer.mvc.logging.spi;

import com.abluepoint.summer.mvc.logging.api.RequestLogRecord;

/**
 * Receives immutable HTTP request log snapshots.
 *
 * <p>Implementations own the output mechanism. They must not read servlet
 * request streams or rely on a shared request object.</p>
 */
@FunctionalInterface
public interface RequestLogSink {

    void record(RequestLogRecord record);
}
