package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.infrastructure.permission.ManagedAuthorizationBootstrap;
import com.abluepoint.summer.sample.enterprise.EnterpriseCreated;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The registry emits this event only in the transaction inserting a new enterprise. */
@Component
class EnterpriseCreatorAuthorization {
  private final ManagedAuthorizationBootstrap installation;

  EnterpriseCreatorAuthorization(ManagedAuthorizationBootstrap installation) {
    this.installation = installation;
  }

  @EventListener
  @Order(100)
  @Transactional(propagation = Propagation.MANDATORY)
  public void create(EnterpriseCreated event) {
    String provenance = "new-enterprise/1\n" + event.enterpriseId() + "\n" + event.creatorSubject();
    String hash;
    try {
      hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(provenance.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    installation.provision(Domain.enterprise(event.enterpriseId()), event.creatorSubject(),
        "new-enterprise:" + event.enterpriseId(), hash, event.requestId());
  }
}
