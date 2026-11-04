package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.member.MemberDirectory.MembershipStatusChanging;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership owns its facts; this synchronous adapter joins its transaction for managed revisions.
 */
@Component
public class MembershipAuthorizationChanges {
  private final com.abluepoint.summer.mvc.request.RequestIdAccessor requestId;
  private final JdbcTemplate db;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;

  public MembershipAuthorizationChanges(
      JdbcTemplate db,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events,
      com.abluepoint.summer.mvc.request.RequestIdAccessor requestId) {
    this.requestId = requestId;
    this.db = db;
    this.audit = audit;
    this.events = events;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void changing(MembershipStatusChanging change) {
    var domain = Domain.enterprise(change.enterpriseId());
    var rows =
        db.queryForList(
            "select engine_mode,revision from summer_authorization_domain where domain_key=? for"
                + " update",
            domain.key());
    if (rows.isEmpty() || !"MANAGED".equals(rows.getFirst().get("engine_mode"))) return;
    var principal = SecurityContextHolder.getContext().getAuthentication();
    audit.mutation(
        domain.key(),
        change.memberId(),
        "MEMBERSHIP_CHANGED",
        requestId.currentRequestId(),
        principal == null ? "" : principal.getName());
    events.enqueue(
        domain,
        "MEMBER",
        change.memberId(),
        "MEMBERSHIP_CHANGED",
        ((Number) rows.getFirst().get("revision")).longValue() + 1,
        requestId.currentRequestId());
  }
}
