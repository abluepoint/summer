package com.abluepoint.summer.sample.authorization.domain.permission;

import java.util.Set;

/** Exact component-owned applicant entries retain their scope, subject and state protocols. */
public final class InterfaceComponentPolicy {
  private InterfaceComponentPolicy() {}

  public static boolean applicant(String owner, String method, String template) {
    return owner.equals("com.abluepoint.summer.sample.invitation.InvitationController")
        && Set.of(
                "POST:/api/v1/enterprises/{enterpriseId}/join-applications",
                "PATCH:/api/v1/enterprises/{enterpriseId}/join-applications/{id}",
                "POST:/api/v1/enterprises/{enterpriseId}/join-applications/{id}:withdraw",
                "POST:/api/v1/invitations/{token}:accept",
                "POST:/api/v1/invitations/{token}:reject")
            .contains(method + ":" + template);
  }
}
