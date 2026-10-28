package com.abluepoint.summer.sample.auditledger.application;


import java.time.Instant;

/** Bounded retention boundary; implementations must delete in finite batches. */
public interface AuditRetentionStore {
    int purgeBefore(Instant cutoff, int batchSize);
}
