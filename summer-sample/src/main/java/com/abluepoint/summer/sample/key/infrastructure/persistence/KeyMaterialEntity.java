package com.abluepoint.summer.sample.key.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import java.time.Instant;

@Entity
@Table(name = "summer_security_keys")
public class KeyMaterialEntity {
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

    public KeyMaterialEntity(String kid, String algorithm, String ciphertext, int version, String status) {
        this(kid, algorithm, ciphertext, version, status, Instant.now(), null);
    }

    public KeyMaterialEntity(String kid, String algorithm, String ciphertext, int version, String status,
                      Instant createdAt, Instant graceUntil) {
        this.kid = kid; this.algorithm = algorithm; this.ciphertext = ciphertext;
        this.version = version; this.status = status; this.createdAt = createdAt; this.graceUntil = graceUntil;
    }

    public String kid() { return kid; }
    public String algorithm() { return algorithm; }
    public String ciphertext() { return ciphertext; }
    public int version() { return version; }
    public String status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant graceUntil() { return graceUntil; }
    public void graceUntil(Instant value) { this.graceUntil = value; }
    public void status(String value) { this.status = value; }
}
