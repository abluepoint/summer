package com.abluepoint.summer.sample.auditledger.api;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.abluepoint.summer.sample.auditledger.application.AuditEventAppendCommand;
public record AuditEventAppendRequest(
        String eventId,
        String eventType,
        String enterpriseId,
        String requestId,
        String traceId,
        String invocationId,
        String attemptId,
        String initiator,
        String actor,
        String effectiveSubject,
        String agentInstanceId,
        Integer policyVersion,
        Long connectionRevision,
        String permitId,
        String permitJti,
        String toolId,
        String resource,
        String action,
        String decision,
        String reasonCode,
        Map<String, Object> detail) { public AuditEventAppendCommand toCommand() { return new AuditEventAppendCommand(eventId,eventType,enterpriseId,requestId,traceId,invocationId,attemptId,initiator,actor,effectiveSubject,agentInstanceId,policyVersion,connectionRevision,permitId,permitJti,toolId,resource,action,decision,reasonCode,detail); } }
