package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import com.abluepoint.summer.security.audit.Slf4jSecurityAuditLogger;
import com.abluepoint.summer.mvc.request.ServletRequestIdAccessor;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.transaction.annotation.Transactional;

class MysqlSummerKeyStore implements SummerKeyStore {
    private final KeyMaterialRepository repository;
    private final KeyRotationStateRepository rotationStates;
    private final byte[] masterKey;
    private final int rotationPeriodDays;
    private final int rotationRetentionDays;
    private final KeyFailureAudit failureAudit;
    private volatile JWKSet current;
    private final ConcurrentMap<String, Rotation> completedRotations = new ConcurrentHashMap<>();

    MysqlSummerKeyStore(KeyMaterialRepository repository, String masterKey) {
        this(repository, null, masterKey, 90, 30);
    }

    MysqlSummerKeyStore(KeyMaterialRepository repository, KeyRotationStateRepository rotationStates,
                        String masterKey, int rotationPeriodDays, int rotationRetentionDays) {
        this(repository, rotationStates, masterKey, rotationPeriodDays, rotationRetentionDays,
                new KeyFailureAudit(new Slf4jSecurityAuditLogger(), new ServletRequestIdAccessor()));
    }

    MysqlSummerKeyStore(KeyMaterialRepository repository, KeyRotationStateRepository rotationStates,
                        String masterKey, int rotationPeriodDays, int rotationRetentionDays, KeyFailureAudit failureAudit) {
        this.repository = repository;
        this.failureAudit = failureAudit;
        this.rotationStates = rotationStates;
        this.rotationPeriodDays = checkedPeriod(rotationPeriodDays);
        this.rotationRetentionDays = checkedRetention(rotationRetentionDays);
        if (masterKey == null || masterKey.isBlank()) throw new IllegalStateException("SUMMER_SECURITY_MASTER_KEY is required");
        this.masterKey = digest(masterKey);
        try {
            this.current = loadOrCreate();
        } catch (RuntimeException exception) {
            failureAudit.readFailed(null);
            throw exception;
        }
        ensureRotationState();
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        try {
            return selectKeys(selector);
        } catch (RuntimeException exception) {
            failureAudit.readFailed(cachedKeyId());
            throw exception;
        }
    }

    private String cachedKeyId() {
        JWKSet snapshot = current;
        return snapshot != null && snapshot.getKeys().size() == 1 ? snapshot.getKeys().getFirst().getKeyID() : null;
    }

    private List<JWK> selectKeys(JWKSelector selector) {
        refreshCurrent();
        List<JWK> selected = selector.select(current);
        JWKMatcher matcher = selector.getMatcher();
        boolean hasKeyId = matcher.getKeyIDs() != null && !matcher.getKeyIDs().isEmpty();
        if (!hasKeyId && !matcher.isPublicOnly()
                && matcher.getKeyTypes() != null && !matcher.getKeyTypes().isEmpty()) {
            return selected.stream().filter(value -> value.getKeyID().equals(activeKid())).limit(1).toList();
        }
        if (hasKeyId) {
            return selected.stream().map(value -> {
                try {
                    if (value instanceof RSAKey rsa) {
                        return new RSAKey.Builder(rsa.toRSAPublicKey()).keyID(rsa.getKeyID()).build();
                    }
                    return value.toPublicJWK();
                } catch (Exception exception) {
                    throw new IllegalStateException("Unable to expose public signing key", exception);
                }
            }).toList();
        }
        return selected;
    }

    JWKSet current() { return current; }

    KeyRotationStateEntity rotationState() {
        return rotationStates == null ? null : ensureRotationState();
    }

    List<Map<String, Object>> metadata() {
        try {
            return readMetadata();
        } catch (RuntimeException exception) {
            failureAudit.readFailed(cachedKeyId());
            throw exception;
        }
    }

    private List<Map<String, Object>> readMetadata() {
        Instant now = Instant.now();
        return repository.findAll().stream()
                .filter(value -> "ACTIVE".equals(value.status()) || ("GRACE".equals(value.status())
                        && (value.graceUntil() == null || value.graceUntil().isAfter(now))))
                .map(value -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("kid", value.kid());
                    result.put("algorithm", value.algorithm());
                    result.put("source", "mysql");
                    result.put("status", value.status());
                    result.put("version", value.version());
                    result.put("health", "READY");
                    if (value.graceUntil() != null) result.put("graceUntil", value.graceUntil().toString());
                    return result;
                })
                .toList();
    }

    int currentVersion() {
        try {
            return repository.findAll().stream().mapToInt(KeyMaterialEntity::version).max().orElse(0);
        } catch (RuntimeException exception) {
            failureAudit.readFailed(cachedKeyId());
            throw exception;
        }
    }

    private KeyRotationStateEntity ensureRotationState() {
        if (rotationStates == null) return null;
        return rotationStates.findById(1).orElseGet(() -> rotationStates.saveAndFlush(
                new KeyRotationStateEntity(rotationPeriodDays, rotationRetentionDays,
                        Instant.now().plus(rotationPeriodDays, ChronoUnit.DAYS))));
    }

    private String activeKid() {
        return repository.findAll().stream().filter(value -> "ACTIVE".equals(value.status()))
                .map(KeyMaterialEntity::kid).findFirst().orElse(null);
    }

    private JWKSet loadOrCreate() {
        List<KeyMaterialEntity> materials = repository.findAll();
        KeyMaterialEntity material = materials.stream()
                .filter(value -> "ACTIVE".equals(value.status())).findFirst().orElse(null);
        if (material == null) {
            try {
                RSAKey generated = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                        .keyID(java.util.UUID.randomUUID().toString()).generate();
                repository.save(new KeyMaterialEntity(generated.getKeyID(), "RS256",
                        encrypt(generated.toRSAPrivateKey().getEncoded()), 1, "ACTIVE"));
                return new JWKSet(generated);
            } catch (Exception exception) {
                throw new IllegalStateException("SUMMER_KEY_INITIALIZATION_FAILED", exception);
            }
        }
        try {
            return toJwkSet(materials);
        } catch (Exception exception) {
            throw new IllegalStateException("SUMMER_KEY_READ_FAILED", exception);
        }
    }

    @Transactional
    synchronized Rotation rotate(int retentionDays, int expectedVersion) {
        return rotate(retentionDays, expectedVersion, null);
    }

    @Transactional
    synchronized Rotation rotate(int retentionDays, int expectedVersion, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Rotation completed = completedRotations.get(idempotencyKey);
            if (completed != null) return completed;
        }
        if (retentionDays < 1 || retentionDays > 365) throw new KeyRotationValidationException("retentionDays must be between 1 and 365");
        List<KeyMaterialEntity> before = repository.findAll();
        int currentVersion = before.stream().mapToInt(KeyMaterialEntity::version).max().orElse(0);
        if (currentVersion != expectedVersion) throw new KeyRotationPreconditionException("The signing key version has changed");
        KeyMaterialEntity old = before.stream().filter(value -> "ACTIVE".equals(value.status())).findFirst().orElseThrow(
                () -> new IllegalStateException("SUMMER_KEY_READ_FAILED"));
        try {
            RSAKey generated = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048)
                    .keyID(java.util.UUID.randomUUID().toString()).generate();
            Instant now = Instant.now();
            old.status("GRACE");
            old.graceUntil(now.plus(retentionDays, ChronoUnit.DAYS));
            KeyMaterialEntity next = new KeyMaterialEntity(generated.getKeyID(), "RS256",
                    encrypt(generated.toRSAPrivateKey().getEncoded()), currentVersion + 1, "ACTIVE", now, null);
            repository.save(old);
            repository.save(next);
            List<JWK> rotatedKeys = new ArrayList<>(current.getKeys());
            rotatedKeys.add(generated);
            JWKSet nextSet = new JWKSet(rotatedKeys);
            current = nextSet;
            Rotation result = new Rotation(next.kid(), next.version(), retentionDays);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) completedRotations.putIfAbsent(idempotencyKey, result);
            markManualRotation();
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("SUMMER_KEY_ROTATION_FAILED", exception);
        }
    }

    private void markManualRotation() {
        if (rotationStates == null) return;
        KeyRotationStateEntity state = ensureRotationState();
        Instant now = Instant.now();
        state.success(now, now.plus(rotationPeriodDays, ChronoUnit.DAYS));
        state.schedule(rotationPeriodDays, rotationRetentionDays, now.plus(rotationPeriodDays, ChronoUnit.DAYS));
        rotationStates.save(state);
    }

    private synchronized void refreshCurrent() {
        try {
            current = toJwkSet(repository.findAll());
        } catch (Exception exception) {
            throw new IllegalStateException("SUMMER_KEY_READ_FAILED", exception);
        }
    }

    private static int checkedPeriod(int value) {
        if (value < 1 || value > 3650) throw new IllegalArgumentException("key rotation period must be between 1 and 3650 days");
        return value;
    }

    private static int checkedRetention(int value) {
        if (value < 1 || value > 365) throw new IllegalArgumentException("key rotation retention must be between 1 and 365 days");
        return value;
    }

    private JWKSet toJwkSet(List<KeyMaterialEntity> materials) throws Exception {
        Instant now = Instant.now();
        List<JWK> keys = new ArrayList<>();
        for (KeyMaterialEntity value : materials) {
            if (!"ACTIVE".equals(value.status()) && !("GRACE".equals(value.status())
                    && (value.graceUntil() == null || value.graceUntil().isAfter(now)))) continue;
            byte[] encoded = decrypt(value.ciphertext());
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
            java.security.interfaces.RSAPublicKey publicKey = (java.security.interfaces.RSAPublicKey)
                    KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                            privateKey.getModulus(), privateKey.getPublicExponent()));
            keys.add(new RSAKey.Builder(publicKey).privateKey(privateKey)
                    .keyID(value.kid()).keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                    .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).build());
        }
        return new JWKSet(keys);
    }

    record Rotation(String activeKid, int version, int retentionDays) {}
    static final class KeyRotationValidationException extends RuntimeException {
        KeyRotationValidationException(String message) { super(message); }
    }
    static final class KeyRotationPreconditionException extends RuntimeException {
        KeyRotationPreconditionException(String message) { super(message); }
    }

    private String encrypt(byte[] plain) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12]; new java.security.SecureRandom().nextBytes(iv);
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(masterKey, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv));
        return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(plain));
    }

    private byte[] decrypt(String value) throws Exception {
        String[] parts = value.split("\\.", 2);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(masterKey, "AES"),
                new javax.crypto.spec.GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
        return cipher.doFinal(Base64.getDecoder().decode(parts[1]));
    }

    private static byte[] digest(String value) {
        try { return java.util.Arrays.copyOf(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), 16); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
