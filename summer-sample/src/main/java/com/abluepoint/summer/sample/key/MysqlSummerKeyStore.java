package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

final class MysqlSummerKeyStore implements SummerKeyStore {
    private final KeyMaterialRepository repository;
    private final byte[] masterKey;
    private volatile JWKSet current;

    MysqlSummerKeyStore(KeyMaterialRepository repository, String masterKey) {
        this.repository = repository;
        if (masterKey == null || masterKey.isBlank()) throw new IllegalStateException("SUMMER_SECURITY_MASTER_KEY is required");
        this.masterKey = digest(masterKey);
        this.current = loadOrCreate();
    }

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        return selector.select(current);
    }

    JWKSet current() { return current; }

    private JWKSet loadOrCreate() {
        KeyMaterialEntity material = repository.findAll().stream()
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
            byte[] encoded = decrypt(material.ciphertext());
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
            java.security.interfaces.RSAPublicKey publicKey = (java.security.interfaces.RSAPublicKey)
                    KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                            privateKey.getModulus(), privateKey.getPublicExponent()));
            return new JWKSet(new RSAKey.Builder(publicKey).privateKey(privateKey)
                    .keyID(material.kid()).algorithm(new com.nimbusds.jose.Algorithm("RS256")).build());
        } catch (Exception exception) {
            throw new IllegalStateException("SUMMER_KEY_READ_FAILED", exception);
        }
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
