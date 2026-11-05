package com.abluepoint.summer.sample.authorization.domain.migration;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public record MigrationPlan(
    String snapshotId,
    String domainKey,
    long sourceRevision,
    List<Permission> permissions,
    List<NarrowRole> roles,
    List<Assignment> assignments,
    List<Mapping> mappings,
    List<String> blockers) {
  public MigrationPlan {
    permissions = List.copyOf(permissions);
    roles = List.copyOf(roles);
    assignments = List.copyOf(assignments);
    mappings = List.copyOf(mappings);
    blockers = List.copyOf(blockers);
  }

  public record Permission(String id, String type, String resourceId, String accessMode) {}

  public record NarrowRole(String id, Set<String> permissionIds, String constraintFingerprint) {
    public NarrowRole {
      permissionIds = Collections.unmodifiableSet(new TreeSet<>(permissionIds));
    }
  }

  public record Assignment(String subject, String roleId, Set<String> sourceRoleIds) {
    public Assignment {
      sourceRoleIds = Collections.unmodifiableSet(new TreeSet<>(sourceRoleIds));
    }
  }

  public record Mapping(String sourceKind, String sourceId, String targetKind, String targetId) {}
}
