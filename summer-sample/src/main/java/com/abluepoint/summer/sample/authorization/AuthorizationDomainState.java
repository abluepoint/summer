package com.abluepoint.summer.sample.authorization;

import jakarta.persistence.*;

@Entity
@Table(name = "summer_authorization_domain")
class AuthorizationDomainState {
    @Id @Column(name = "domain_key", length = 96) private String key;
    @Column(name = "engine_mode", nullable = false, length = 32) private String mode;
    @Column(name = "revision", nullable = false) private long revision;
    protected AuthorizationDomainState() {}
}
