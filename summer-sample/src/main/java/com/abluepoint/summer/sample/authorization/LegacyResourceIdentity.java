package com.abluepoint.summer.sample.authorization;

import jakarta.persistence.*;

/** Transitional identity ledger. Legacy codes never enter the new resource contract. */
@Entity
@Table(name = "summer_authorization_resource_identity")
class LegacyResourceIdentity {
    @Id @Column(name = "resource_id", length = 96) private String id;
    @Column(name = "locator_hash", nullable = false, unique = true, length = 64) private String locator;
    @Column(name = "legacy_code", nullable = false, length = 200) private String code;
    protected LegacyResourceIdentity() {}
}
