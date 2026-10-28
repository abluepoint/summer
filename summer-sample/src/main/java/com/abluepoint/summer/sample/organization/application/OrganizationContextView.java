package com.abluepoint.summer.sample.organization.application;
import java.util.*;
import java.time.Instant;

public record OrganizationContextView(String subject, String enterpriseId, String organizationId, String revision) {}
