package com.abluepoint.summer.sample.authorization.application.permission;

import com.abluepoint.summer.sample.resource.BusinessOperationHints;
import com.abluepoint.summer.sample.resource.BusinessResourceAccess;
import com.abluepoint.summer.sample.resource.RuntimeBusinessResources;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class BusinessOperationQualifications implements BusinessOperationHints {
  private final BusinessResourceAccess resources;
  private final ObjectProvider<RuntimeBusinessResources> installed;

  public BusinessOperationQualifications(BusinessResourceAccess resources,
      ObjectProvider<RuntimeBusinessResources> installed) {
    this.resources = resources; this.installed = installed;
  }

  public boolean supports(String action) { return Set.of("messages.send", "messages.ack").contains(action); }

  public boolean allowed(Domain domain, Authentication actor, String action) {
    if (!supports(action) || !"ENTERPRISE".equals(domain.kind())) return false;
    try {
      if ("messages.ack".equals(action))
        return resources.requireHttp(domain, actor, "POST",
            "/api/v1/enterprises/{enterpriseId}/messages/{messageId}:ack", "enterprise.messages.write");
      var owners = installed.orderedStream()
          .filter(owner -> org.springframework.util.ClassUtils.getUserClass(owner).getName().equals("com.abluepoint.summer.sample.realtime.RealtimeAccess"))
          .toList();
      if (owners.size() != 1) return false;
      var owner = owners.getFirst();
      // The hub rechecks read before processing any send frame, so both independent interfaces apply.
      return resources.requireRuntime(domain, actor, owner, "read", "enterprise.messages.read")
          && resources.requireRuntime(domain, actor, owner, "send", "enterprise.messages.write");
    } catch (AccessDeniedException denied) { return false; }
  }
}
