package com.abluepoint.summer.sample.key;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import java.time.Instant;

@Entity
@Table(name = "summer_security_keys")
class KeyMaterialEntity {
    @Id
    private String kid;
    private String algorithm;
    @Lob
    private String ciphertext;
    private int version;
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "grace_until")
    private Instant graceUntil;

    protected KeyMaterialEntity() {}

    KeyMaterialEntity(String kid, String algorithm, String ciphertext, int version, String status) {
        this(kid, algorithm, ciphertext, version, status, Instant.now(), null);
    }

    KeyMaterialEntity(String kid, String algorithm, String ciphertext, int version, String status,
                      Instant createdAt, Instant graceUntil) {
        this.kid = kid; this.algorithm = algorithm; this.ciphertext = ciphertext;
        this.version = version; this.status = status; this.createdAt = createdAt; this.graceUntil = graceUntil;
    }

    String kid() { return kid; }
    String algorithm() { return algorithm; }
    String ciphertext() { return ciphertext; }
    int version() { return version; }
    String status() { return status; }
    Instant createdAt() { return createdAt; }
    Instant graceUntil() { return graceUntil; }
    void graceUntil(Instant value) { this.graceUntil = value; }
    void status(String value) { this.status = value; }
}
