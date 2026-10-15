package com.abluepoint.summer.sample.observability;

import com.abluepoint.summer.mvc.logging.RequestLogRecord;
import com.abluepoint.summer.mvc.logging.RequestLogSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consumes the MVC snapshot only. Monitoring never emits request bodies. */
final class TracedRequestLogSink implements RequestLogSink {
    private static final Logger log = LoggerFactory.getLogger("com.abluepoint.summer.mvc.requestlog");

    @Override
    public void record(RequestLogRecord record) {
        log.info("http_request requestId={} method={} path={} status={} durationMs={} traceId={}",
                record.requestId(), record.method(), record.path(), record.status(),
                record.durationMillis(), TraceCorrelation.currentTraceId());
    }
}
