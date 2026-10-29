package com.abluepoint.summer.sample.organization.domain;
import java.util.*;
import java.time.Instant;

public record OrganizationNode(String enterpriseId, String id, String parentId, String unitType, String name, String code, String leaderMemberId,
                              String description, int sortOrder, String status, long version) {
        public OrganizationNode withVersion(long value) { return new OrganizationNode(enterpriseId, id, parentId, unitType, name, code, leaderMemberId, description, sortOrder, status, value); }

    @Override public String toString() { return "UnitRecord[" + "enterpriseId=" + enterpriseId + ", " + "id=" + id + ", " + "parentId=" + parentId + ", " + "unitType=" + unitType + ", " + "name=" + name + ", " + "code=" + code + ", " + "leaderMemberId=" + leaderMemberId + ", " + "description=" + description + ", " + "sortOrder=" + sortOrder + ", " + "status=" + status + ", " + "version=" + version + "]"; }
}
