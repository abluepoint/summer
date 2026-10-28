package com.abluepoint.summer.sample.realtime.infrastructure;
/** Locks the existing tenant row inside the caller's write transaction. */
@org.springframework.stereotype.Component
public final class RealtimeTenantLock {
 private final org.springframework.jdbc.core.JdbcTemplate jdbc;
 public RealtimeTenantLock(org.springframework.jdbc.core.JdbcTemplate jdbc) { this.jdbc=jdbc; }
 public void acquire(String enterpriseId) { jdbc.queryForObject("select enterprise_id from summer_enterprise where enterprise_id=? for update",String.class,enterpriseId); }
}
