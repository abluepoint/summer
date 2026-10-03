package com.abluepoint.summer.sample.enterprise;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
final class EnterpriseReviewService {
    private final Map<String, EnterpriseReview> applications = new ConcurrentHashMap<>();
    private final Map<String, List<ReviewDecision>> audit = new ConcurrentHashMap<>();
    private final Map<String, Receipt> idempotency = new ConcurrentHashMap<>();

    EnterpriseReviewService() {
        applications.put("application-acme", new EnterpriseReview("application-acme", "ent-acme", "Acme Corporation", "PENDING",
                List.of(new MaterialMetadata("business-license.pdf", "application/pdf", 1024)), "seed"));
    }

    List<EnterpriseReview> list(Authentication auth) { require(auth, "platform.enterprise-review.read"); return applications.values().stream().sorted(java.util.Comparator.comparing(EnterpriseReview::applicationId)).toList(); }
    EnterpriseReview detail(String id, Authentication auth) { require(auth, "platform.enterprise-review.read"); EnterpriseReview r = applications.get(id); if (r == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND); return r; }

    synchronized EnterpriseReview decide(String id, String decision, String reason, String ifMatch, String key, String requestId, Authentication auth) {
        require(auth, "platform.enterprise-review.write");
        if (key == null || key.isBlank()) throw new ReviewConflictException("Idempotency-Key is required");
        String fingerprint = decision + "|" + reason + "|" + ifMatch;
        String operation = id + ":" + key;
        Receipt previous = idempotency.get(operation);
        if (previous != null) { if (!previous.fingerprint().equals(fingerprint)) throw new ReviewConflictException("Idempotency-Key was already used for another decision"); return previous.review(); }
        EnterpriseReview current = applications.get(id);
        if (current == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        if (!quote(current.revision()).equals(ifMatch)) throw new ReviewPreconditionException();
        if (("REJECT".equals(decision) || "REQUEST_INFO".equals(decision)) && (reason == null || reason.isBlank())) throw new ReviewValidationException("reason is required");
        String after = switch (decision) { case "APPROVE" -> "APPROVED"; case "REJECT" -> "REJECTED"; case "REQUEST_INFO" -> "NEEDS_INFO"; default -> throw new ReviewValidationException("unsupported decision"); };
        if (!"PENDING".equals(current.status()) && !"NEEDS_INFO".equals(current.status())) throw new ReviewValidationException("illegal status transition");
        EnterpriseReview updated = new EnterpriseReview(current.applicationId(), current.enterpriseId(), current.legalName(), after, current.materials(), revision(current.applicationId(), after));
        applications.put(id, updated);
        ReviewDecision event = new ReviewDecision(auth.getName(), id, decision, reason, current.status(), after, requestId);
        audit.computeIfAbsent(id, ignored -> new ArrayList<>()).add(event);
        idempotency.put(operation, new Receipt(fingerprint, updated));
        return updated;
    }

    List<ReviewDecision> audit(String id, Authentication auth) { require(auth, "platform.enterprise-review.read"); detail(id, auth); return List.copyOf(audit.getOrDefault(id, List.of())); }

    private void require(Authentication auth, String capability) {
        if (!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("platform capability required");
        Object claim = jwt.getToken().getClaims().get("platform_capabilities");
        if (!(claim instanceof Collection<?> values) || values.stream().noneMatch(v -> capability.equals(String.valueOf(v)))) throw new AccessDeniedException("platform capability required");
    }
    private static String revision(String id, String status) { try { return hex(MessageDigest.getInstance("SHA-256").digest((id + ":" + status).getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static String quote(String value) { return "\"" + value + "\""; }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) s.append(String.format("%02x", b)); return s.toString(); }
    private record Receipt(String fingerprint, EnterpriseReview review) {}
}
