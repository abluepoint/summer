package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.domain.data.OrganizationAuthorizationChange;
import com.abluepoint.summer.sample.member.MemberDirectory.AuthorizationChanging;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
public class OrganizationAuthorizationChanges {
  private final JdbcTemplate db;
  private final AuthorizationAuditStore audit;
  private final AuthorizationChangeEventService events;
  private final RequestIdAccessor requestIds;

  public OrganizationAuthorizationChanges(
      JdbcTemplate db,
      AuthorizationAuditStore audit,
      AuthorizationChangeEventService events,
      RequestIdAccessor requestIds) {
    this.db = db;
    this.audit = audit;
    this.events = events;
    this.requestIds = requestIds;
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void changed(OrganizationAuthorizationChange change) {
    apply(change, "ORGANIZATION");
  }

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void membership(AuthorizationChanging change) {
    apply(
        new OrganizationAuthorizationChange(
            change.enterpriseId(),
            change.memberId(),
            "MEMBERSHIP_PROVISIONED",
            null,
            change.lockOnly()),
        "MEMBER");
  }

  private void apply(OrganizationAuthorizationChange change, String resourceType) {
    var domain = Domain.enterprise(change.enterpriseId());
    var rows =
        db.queryForList(
            "select engine_mode,revision from summer_authorization_domain where domain_key=? for"
                + " update",
            domain.key());
    if (rows.isEmpty()
        || !"MANAGED".equals(rows.getFirst().get("engine_mode"))
        || change.lockOnly()) return;
    var auth = SecurityContextHolder.getContext().getAuthentication();
    String requestId =
        change.requestId() == null ? requestIds.currentRequestId() : change.requestId();
    audit.mutation(
        domain.key(),
        change.resourceId(),
        change.action(),
        requestId,
        auth == null ? "" : auth.getName());
    events.enqueue(
        domain,
        resourceType,
        change.resourceId(),
        change.action(),
        ((Number) rows.getFirst().get("revision")).longValue() + 1,
        requestId);
  }
}
