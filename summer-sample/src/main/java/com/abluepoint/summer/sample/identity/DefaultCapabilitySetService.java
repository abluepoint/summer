package com.abluepoint.summer.sample.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
final class DefaultCapabilitySetService implements CapabilitySetService {

    private static final String PLATFORM_WORKSPACE = "platform";
    private static final String ENTERPRISE_WORKSPACE = "enterprise";

    @Override
    public CapabilitySet platform(Authentication authentication) {
        List<String> codes = claimList(authentication, "platform_capabilities");
        // The sample has no IAM persistence yet; keep a deterministic read
        // capability so the configured client can exercise the contract.
        if (codes.isEmpty()) {
            codes = List.of("platform.capabilities.read");
        }
        return capabilitySet(PLATFORM_WORKSPACE, null, codes, authentication);
    }

    @Override
    public CapabilitySet enterprise(String enterpriseId, Authentication authentication) {
        if (!isActiveMember(authentication, enterpriseId)) {
            throw new AccessDeniedException("The principal is not an active member of the enterprise context");
        }
        List<String> codes = enterpriseClaimList(authentication, enterpriseId);
        // A production IAM implementation replaces this fixture with a
        // role/policy projection. The context membership check above remains
        // authoritative in either implementation.
        if (codes.isEmpty()) {
            codes = List.of("enterprise.capabilities.read");
        }
        return capabilitySet(ENTERPRISE_WORKSPACE, enterpriseId, codes, authentication);
    }

    private static CapabilitySet capabilitySet(
            String workspace,
            String enterpriseId,
            List<String> codes,
            Authentication authentication
    ) {
        List<String> normalizedCodes = codes.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        return new CapabilitySet(
                workspace,
                enterpriseId,
                normalizedCodes,
                revision(workspace, enterpriseId, normalizedCodes, authentication)
        );
    }

    private static List<String> claimList(Authentication authentication, String claimName) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return List.of();
        }
        Object claim = jwt.getToken().getClaims().get(claimName);
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private static List<String> enterpriseClaimList(Authentication authentication, String enterpriseId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return List.of();
        }
        Object claim = jwt.getToken().getClaims().get("enterprise_capabilities");
        if (!(claim instanceof Map<?, ?> values)) {
            return List.of();
        }
        Object enterpriseCodes = values.get(enterpriseId);
        if (!(enterpriseCodes instanceof Collection<?> codes)) {
            return List.of();
        }
        return codes.stream().map(String::valueOf).toList();
    }

    private static boolean isActiveMember(Authentication authentication, String enterpriseId) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return false;
        }
        Object claim = jwt.getToken().getClaims().get("enterprise_memberships");
        if (!(claim instanceof Collection<?> values)) {
            return false;
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(value -> enterpriseId.equals(stringValue(value, "enterpriseId", "enterprise_id"))
                        && "ACTIVE".equalsIgnoreCase(stringValue(value, "status", "membership_status")));
    }

    private static String stringValue(Map<?, ?> values, String firstKey, String secondKey) {
        Object value = values.get(firstKey);
        if (value == null) {
            value = values.get(secondKey);
        }
        return value == null ? null : value.toString();
    }

    private static String revision(
            String workspace,
            String enterpriseId,
            List<String> codes,
            Authentication authentication
    ) {
        String authorities = authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        String input = workspace + "|" + enterpriseId + "|" + authentication.getName()
                + "|" + authorities + "|" + codes;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
