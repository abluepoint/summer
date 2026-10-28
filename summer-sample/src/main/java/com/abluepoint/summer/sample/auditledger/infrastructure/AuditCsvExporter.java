package com.abluepoint.summer.sample.auditledger.infrastructure;
import java.util.List;
import com.abluepoint.summer.sample.auditledger.application.AuditEventView;
public final class AuditCsvExporter {
 private AuditCsvExporter() {}
 public static String export(List<AuditEventView> filtered) {
        StringBuilder csv = new StringBuilder("eventId,eventType,enterpriseId,requestId,traceId,invocationId,attemptId,initiator,actor,effectiveSubject,agentInstanceId,toolId,resource,action,decision,reasonCode,occurredAt,eventDigest\n");
        for (AuditEventView event : filtered) {
            csv.append(String.join(",", csvValue(event.eventId()), csvValue(event.eventType()),
                    csvValue(event.enterpriseId()), csvValue(event.requestId()), csvValue(event.traceId()),
                    csvValue(event.invocationId()), csvValue(event.attemptId()), csvValue(event.initiator()),
                    csvValue(event.actor()), csvValue(event.effectiveSubject()), csvValue(event.agentInstanceId()),
                    csvValue(event.toolId()), csvValue(event.resource()), csvValue(event.action()),
                    csvValue(event.decision()), csvValue(event.reasonCode()), csvValue(event.occurredAt()),
                    csvValue(event.eventDigest()))).append('\n');
        }
        return csv.toString();
 }
    private static String csvValue(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        return '"' + text.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }
}
