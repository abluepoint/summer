package com.abluepoint.summer.sample.authorization.application.permission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared publication tombstone: a deliberately empty manifest remains published. */
@Service
public class MenuPublicationState
    implements com.abluepoint.summer.sample.authorization.MenuPublication {
  private final JdbcTemplate db;

  public MenuPublicationState(JdbcTemplate db) {
    this.db = db;
  }

  public Snapshot read() {
    var rows =
        db.queryForList("select * from summer_menu_publication where publication_id='global'");
    if (rows.size() != 1)
      throw new org.springframework.dao.DataRetrievalFailureException(
          "Missing menu publication state");
    var row = rows.getFirst();
    return new Snapshot(
        Boolean.TRUE.equals(row.get("published")),
        ((Number) row.get("manifest_version")).longValue());
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void lock(
      com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain) {
    db.update(
        "insert into summer_authorization_domain(domain_key,engine_mode,revision) values"
            + " (?,'LEGACY',0) on duplicate key update domain_key=domain_key",
        domain.key());
    db.queryForList(
        "select domain_key from summer_authorization_domain where domain_key=? for update",
        domain.key());
    if (db.queryForList(
                "select publication_id from summer_menu_publication where publication_id='global'"
                    + " for update")
            .size()
        != 1)
      throw new org.springframework.dao.DataRetrievalFailureException(
          "Missing menu publication state");
  }

  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void published(long version) {
    db.update(
        "update summer_menu_publication set published=true,manifest_version=? where"
            + " publication_id='global'",
        version);
  }
}
