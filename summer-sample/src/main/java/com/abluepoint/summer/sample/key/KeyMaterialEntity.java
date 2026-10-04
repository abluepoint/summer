package com.abluepoint.summer.sample.key;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Lob;

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

    protected KeyMaterialEntity() {}

    KeyMaterialEntity(String kid, String algorithm, String ciphertext, int version, String status) {
        this.kid = kid; this.algorithm = algorithm; this.ciphertext = ciphertext;
        this.version = version; this.status = status;
    }

    String kid() { return kid; }
    String algorithm() { return algorithm; }
    String ciphertext() { return ciphertext; }
    int version() { return version; }
    String status() { return status; }
}
