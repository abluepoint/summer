package com.abluepoint.summer.sample.connection.domain;
/** Monotonic catalog publication and service lifecycle constraints. */
public final class ConnectionCatalogRules {
 private ConnectionCatalogRules() {}
 public static void requireStatus(String status) {
  if (!java.util.Set.of("ACTIVE", "DISABLED", "REVOKED").contains(status)) throw new ConnectionValidationException("Invalid connection service status");
 }
 public static boolean isReplay(long revision, String digest, long currentRevision, String currentDigest) {
  if (revision < currentRevision) throw new ConnectionConflictException("Stale catalog revision");
  if (revision == currentRevision && !digest.equals(currentDigest)) throw new ConnectionConflictException("Catalog revision conflict");
  return revision == currentRevision;
 }
}
