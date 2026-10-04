package com.abluepoint.summer.sample.realtime;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
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

    RealtimeMessageService(RealtimeMessageRepository repository, ObjectMapper objectMapper,
                           ApplicationEventPublisher events, RequestIdAccessor requestId,
                           ObjectProvider<RealtimeMessageNodeRelay> nodeRelay) {
        this.repository = repository; this.objectMapper = objectMapper; this.events = events; this.requestId = requestId; this.nodeRelay = nodeRelay;
    }

    synchronized RealtimeMessageView send(String enterpriseId, RealtimeMessageCreateRequest request, Authentication auth) {
        require(enterpriseId, auth, WRITE);
        validate(request);
        String sender = auth.getName();
        var existing = repository.findByEnterpriseIdAndSenderAndClientMessageId(enterpriseId, sender, request.clientMessageId());
        if (existing.isPresent()) return view(existing.get(), true);
        long next = repository.findFirstByEnterpriseIdOrderBySequenceDesc(enterpriseId).map(e -> e.sequence() + 1).orElse(1L);
        Instant expires = request.ttlSeconds() == null ? null : Instant.now().plusSeconds(request.ttlSeconds());
        String payload = encode(request.payload());
        RealtimeMessageEntity entity = repository.saveAndFlush(new RealtimeMessageEntity(UUID.randomUUID().toString(), enterpriseId,
                sender, request.clientMessageId(), blank(request.recipient()) ? null : request.recipient().trim(), payload, next, expires));
        RealtimeMessageView result = view(entity, false);
        events.publishEvent(new RealtimeMessagePublishedEvent(result));
        nodeRelay.ifAvailable(relay -> relay.publish(result));
        return result;
    }

    synchronized RealtimeMessageView acknowledge(String enterpriseId, String messageId, Authentication auth) {
        require(enterpriseId, auth, WRITE);
        RealtimeMessageEntity entity = repository.findById(messageId)
                .filter(e -> enterpriseId.equals(e.enterpriseId())).orElseThrow(RealtimeMessageNotFoundException::new);
        entity.acknowledge();
        return view(repository.saveAndFlush(entity), false);
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

    static void require(String enterpriseId, Authentication auth, String... capabilities) {
        if (!(auth instanceof JwtAuthenticationToken jwt) || blank(enterpriseId) || !activeMember(jwt, enterpriseId)
                || !hasCapability(jwt, enterpriseId, capabilities)
                || Arrays.stream(capabilities).noneMatch(cap -> jwt.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + cap).equals(a.getAuthority()))))
            throw new AccessDeniedException("Message capability denied");
    }

    private static boolean activeMember(JwtAuthenticationToken jwt, String enterpriseId) {
        Object value = jwt.getToken().getClaims().get("enterprise_memberships");
        return value instanceof Collection<?> items && items.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(m -> enterpriseId.equals(String.valueOf(m.getOrDefault("enterpriseId", m.get("enterprise_id"))))
                        && "ACTIVE".equalsIgnoreCase(String.valueOf(m.getOrDefault("status", m.get("membership_status")))));
    }

    private static boolean hasCapability(JwtAuthenticationToken jwt, String enterpriseId, String... capabilities) {
        Object value = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(value instanceof Map<?, ?> map) || !(map.get(enterpriseId) instanceof Collection<?> granted)) return false;
        return granted.stream().map(String::valueOf).anyMatch(g -> Arrays.asList(capabilities).contains(g));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
