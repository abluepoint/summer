package com.abluepoint.summer.sample.auditledger;

import java.time.Instant;
import java.util.List;

/** Transactional outbox boundary used to publish audit notifications after commit. */
public interface AuditOutboxStore {
    AuditOutboxEntity enqueue(AuditOutboxEntity message);

    List<AuditOutboxEntity> findDue(Instant now, int limit);

    void markPublished(long id);

    void reschedule(long id, Instant nextAttemptAt);

    void deadLetter(long id);
}
