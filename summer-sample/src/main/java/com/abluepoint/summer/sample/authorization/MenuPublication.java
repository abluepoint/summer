package com.abluepoint.summer.sample.authorization;

/** Shared transaction boundary for legacy and managed menu publishers. */
public interface MenuPublication {
  record Snapshot(boolean published, long manifestVersion) {}

  Snapshot read();

  /** The caller must already hold its authorization domain lock when applicable. */
  void lock(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain);

  void published(long version);
}
