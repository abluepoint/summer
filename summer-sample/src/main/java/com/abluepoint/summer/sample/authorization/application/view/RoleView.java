package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record RoleView(String roleId, String enterpriseId, String name, String code, boolean builtIn, String status,
                List<String> capabilityIds, long version, String bundleId) {}
