package com.abluepoint.summer.sample.dictionary;

import java.time.Instant;

final class DictionaryVersionRequest {
    private Object expectedVersion;
    private Object expectedDraftRevision;
    private Object expectedEffectiveRevision;
    public Object expectedDraftRevision() { return expectedDraftRevision; }
    public Object expectedEffectiveRevision() { return expectedEffectiveRevision; }
    public void setExpectedDraftRevision(Object value) { expectedDraftRevision = value; }
    public void setExpectedEffectiveRevision(Object value) { expectedEffectiveRevision = value; }
    private Object targetVersion;
    private String idempotencyKey;

    public Object expectedVersion() { return expectedVersion; }
    public Object targetVersion() { return targetVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public void setExpectedVersion(Object value) { expectedVersion = value; }
    public void setVersion(Object value) { if (expectedVersion == null) expectedVersion = value; }
    public void setRevision(Object value) { if (expectedVersion == null) expectedVersion = value; }
    public void setTargetVersion(Object value) { targetVersion = value; }
    public void setRollbackVersion(Object value) { targetVersion = value; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
}

record DictionaryVersionView(long version, String status, Instant createdAt, String createdBy) {}
record DictionaryTypeVersionView(String code, String name, long version, String status, long draftRevision, long publishedVersion, long effectiveRevision) {}
