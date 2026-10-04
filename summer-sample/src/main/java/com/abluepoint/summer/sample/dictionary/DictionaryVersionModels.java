package com.abluepoint.summer.sample.dictionary;

import java.time.Instant;

final class DictionaryVersionRequest {
    private Long expectedVersion;
    private Long targetVersion;
    private String idempotencyKey;

    public Long expectedVersion() { return expectedVersion; }
    public Long targetVersion() { return targetVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public void setExpectedVersion(Long value) { expectedVersion = value; }
    public void setVersion(Long value) { if (expectedVersion == null) expectedVersion = value; }
    public void setRevision(Long value) { if (expectedVersion == null) expectedVersion = value; }
    public void setTargetVersion(Long value) { targetVersion = value; }
    public void setRollbackVersion(Long value) { targetVersion = value; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
}

record DictionaryVersionView(long version, String status, Instant createdAt, String createdBy) {}
record DictionaryTypeVersionView(String code, String name, long version, String status) {}
