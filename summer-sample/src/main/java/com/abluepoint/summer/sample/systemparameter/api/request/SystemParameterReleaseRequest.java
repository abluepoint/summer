package com.abluepoint.summer.sample.systemparameter.api.request;

import com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterReleaseCommand;

public final class SystemParameterReleaseRequest {
    private Long revision;
    private Long expectedVersion;
    private String idempotencyKey;

    public Long revision() { return revision; }
    public Long expectedVersion() { return expectedVersion; }
    public String idempotencyKey() { return idempotencyKey; }
    public void setRevision(Long revision) { this.revision = revision; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterReleaseCommand toCommand() { return new com.abluepoint.summer.sample.systemparameter.application.command.SystemParameterReleaseCommand(revision,expectedVersion,idempotencyKey); }
}
