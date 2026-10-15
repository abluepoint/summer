package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class RealtimeMessageService {
    static final String READ = "enterprise.messages.read";
    static final String WRITE = "enterprise.messages.write";
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    private static final int MAX_TTL_SECONDS = 24 * 60 * 60;
    private final RealtimeMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final RequestIdAccessor requestId;
    private final ObjectProvider<RealtimeMessageNodeRelay> nodeRelay;
    private final RealtimeAccess access;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate writes;

    RealtimeMessageService(RealtimeMessageRepository repository, ObjectMapper objectMapper,
                           ApplicationEventPublisher events, RequestIdAccessor requestId,
                           ObjectProvider<RealtimeMessageNodeRelay> nodeRelay, RealtimeAccess access,
                           JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.repository = repository; this.objectMapper = objectMapper; this.events = events; this.requestId = requestId; this.nodeRelay = nodeRelay;
        this.access = access; this.jdbc = jdbc; this.writes = new TransactionTemplate(transactions);
        this.writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    RealtimeMessageView send(String enterpriseId, RealtimeMessageCreateRequest request, Authentication auth) {
        require(enterpriseId, auth, WRITE);
        validate(request);
        RealtimeMessageView result = writes.execute(status -> {
            lockEnterprise(enterpriseId);
            require(enterpriseId, auth, WRITE);
            String sender = auth.getName();
            var existing = repository.findByEnterpriseIdAndSenderAndClientMessageId(enterpriseId, sender, request.clientMessageId());
            if (existing.isPresent()) return view(existing.get(), true);
            long next = repository.findFirstByEnterpriseIdOrderBySequenceDesc(enterpriseId).map(e -> e.sequence() + 1).orElse(1L);
            Instant expires = request.ttlSeconds() == null ? null : Instant.now().plusSeconds(request.ttlSeconds());
            RealtimeMessageEntity entity = repository.saveAndFlush(new RealtimeMessageEntity(UUID.randomUUID().toString(), enterpriseId,
                    sender, request.clientMessageId(), blank(request.recipient()) ? null : request.recipient().trim(),
                    encode(request.payload()), next, expires));
            return view(entity, false);
        });
        // No frame or cross-node notification can describe an uncommitted message.
        if (!result.duplicate()) {
            events.publishEvent(new RealtimeMessagePublishedEvent(result));
            nodeRelay.ifAvailable(relay -> relay.publish(result));
        }
        return result;
    }

    RealtimeMessageView acknowledge(String enterpriseId, String messageId, Authentication auth) {
        require(enterpriseId, auth, WRITE);
        return writes.execute(status -> {
            lockEnterprise(enterpriseId);
            require(enterpriseId, auth, WRITE);
            RealtimeMessageEntity entity = repository.findById(messageId)
                    .filter(e -> enterpriseId.equals(e.enterpriseId())).orElseThrow(RealtimeMessageNotFoundException::new);
            entity.acknowledge();
            return view(repository.saveAndFlush(entity), false);
        });
    }

    private void lockEnterprise(String enterpriseId) {
        // All JVMs serialize sequence allocation and deduplication on the same durable tenant row.
        jdbc.queryForObject("select enterprise_id from summer_enterprise where enterprise_id=? for update", String.class, enterpriseId);
    }

    RealtimeMessagePage list(String enterpriseId, long after, Authentication auth) {
        require(enterpriseId, auth, READ);
        List<RealtimeMessageEntity> entities = after == 0
                ? repository.findTop500ByEnterpriseIdOrderBySequenceAsc(enterpriseId)
                : repository.findTop500ByEnterpriseIdAndSequenceGreaterThanOrderBySequenceAsc(enterpriseId, after);
        long latest = entities.stream().mapToLong(RealtimeMessageEntity::sequence).max()
                .orElseGet(() -> repository.findFirstByEnterpriseIdOrderBySequenceDesc(enterpriseId).map(RealtimeMessageEntity::sequence).orElse(after));
        return new RealtimeMessagePage(entities.stream().map(e -> view(e, false)).toList(), latest, entities.size() == 500);
    }

    RealtimeMessageView find(String enterpriseId, String messageId, Authentication auth) {
        require(enterpriseId, auth, READ);
        return view(repository.findById(messageId).filter(e -> enterpriseId.equals(e.enterpriseId()))
                .orElseThrow(RealtimeMessageNotFoundException::new), false);
    }

    static long cursor(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try { long value = Long.parseLong(raw.trim()); if (value < 0) throw new NumberFormatException(); return value; }
        catch (NumberFormatException e) { throw new RealtimeMessageCursorException(); }
    }

    private RealtimeMessageView view(RealtimeMessageEntity e, boolean duplicate) {
        Map<String, Object> payload;
        try { payload = objectMapper.readValue(e.payloadJson(), Map.class); }
        catch (RuntimeException ignored) { payload = Map.of(); }
        return new RealtimeMessageView(e.messageId(), e.enterpriseId(), e.clientMessageId(), e.sender(), e.recipient(),
                payload == null ? Map.of() : Map.copyOf(payload), e.sequence(), e.status(), e.createdAt(), e.acknowledgedAt(), duplicate);
    }

    private String encode(Map<String, Object> payload) {
        try {
            String result = objectMapper.writeValueAsString(payload);
            if (result.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) throw new RealtimeMessageValidationException("Message payload is too large");
            return result;
        } catch (JacksonException e) { throw new RealtimeMessageValidationException("Message payload is invalid"); }
    }

    private static void validate(RealtimeMessageCreateRequest request) {
        if (request == null || blank(request.clientMessageId()) || !request.clientMessageId().matches("[A-Za-z0-9._:-]{1,128}"))
            throw new RealtimeMessageValidationException("clientMessageId is required and must be a safe identifier");
        if (request.payload() == null || request.payload().isEmpty()) throw new RealtimeMessageValidationException("payload must not be empty");
        if (request.ttlSeconds() != null && (request.ttlSeconds() < 1 || request.ttlSeconds() > MAX_TTL_SECONDS))
            throw new RealtimeMessageValidationException("ttlSeconds is outside the supported range");
        if (!blank(request.recipient()) && request.recipient().trim().length() > 128) throw new RealtimeMessageValidationException("recipient is too long");
    }

    void require(String enterpriseId, Authentication authentication, String capability) {
        access.require(enterpriseId, authentication, capability);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
