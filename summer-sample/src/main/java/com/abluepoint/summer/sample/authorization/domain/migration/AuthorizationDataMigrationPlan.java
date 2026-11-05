package com.abluepoint.summer.sample.authorization.domain.migration;

import java.util.List;
import java.util.Map;

/** Derived proof only. The original identity plan and source evidence are immutable. */
public record AuthorizationDataMigrationPlan(
    String snapshotId, String domainKey, String sourceFingerprint, String proofVersion,
    String registryFingerprint, List<Inventory> inventory, List<Item> items,
    List<Grant> grants, List<Vector> vectors, List<String> blockers) {
  public AuthorizationDataMigrationPlan {
    inventory = List.copyOf(inventory);
    items = List.copyOf(items);
    grants = List.copyOf(grants);
    vectors = List.copyOf(vectors);
    blockers = List.copyOf(blockers);
  }
  public record Inventory(String sourcePath, String resource, String subjectType, String subjectId) {}
  public record Item(String subjectKind, String subject, String resource, String action,
      String status, List<String> sourcePaths, List<String> permissionIds,
      String reason, Map<String, String> witness) {
    public Item {
      sourcePaths = List.copyOf(sourcePaths);
      permissionIds = List.copyOf(permissionIds);
      witness = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(witness));
    }
  }
  public record Grant(String id, String resource, String action, String preset, List<String> departmentIds) {
    public Grant { departmentIds = List.copyOf(departmentIds); }
  }
  public record Vector(String subject, String originalRoleId, String targetRoleId,
      List<String> sourceRoleIds, List<String> interfaceMenuPermissionIds,
      List<String> dataPermissionIds, String vectorHash) {
    public Vector {
      sourceRoleIds = List.copyOf(sourceRoleIds);
      interfaceMenuPermissionIds = List.copyOf(interfaceMenuPermissionIds);
      dataPermissionIds = List.copyOf(dataPermissionIds);
    }
  }
}
