package com.abluepoint.summer.sample.organization.domain;
import java.util.*;
import java.time.Instant;

public record OrganizationAssignment(String enterpriseId, String organizationId, String memberId, boolean primary) {
    @Override public String toString() { return "Assignment[" + "enterpriseId=" + enterpriseId + ", " + "organizationId=" + organizationId + ", " + "memberId=" + memberId + ", " + "primary=" + primary + "]"; }
}
