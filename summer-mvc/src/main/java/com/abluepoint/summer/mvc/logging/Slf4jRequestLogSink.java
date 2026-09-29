package com.abluepoint.summer.mvc.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default structured request metadata output for applications using SLF4J. */
public final class Slf4jRequestLogSink implements RequestLogSink {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.abluepoint.summer.mvc.requestlog");

    @Override
    public void record(RequestLogRecord record) {
        if (record.bodyPreview() != null) {
            LOGGER.info(
                    "http_request requestId={} method={} path={} status={} durationMs={} bodyPreview={}",
                    record.requestId(),
                    record.method(),
                    record.path(),
                    record.status(),
                    record.durationMillis(),
                    record.bodyPreview()
            );
            return;
        }
        LOGGER.info(
                "http_request requestId={} method={} path={} status={} durationMs={}",
                record.requestId(),
                record.method(),
                record.path(),
                record.status(),
                record.durationMillis()
        );
    }
}
