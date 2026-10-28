package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record UserRoleAssignmentView(String username, String enterpriseId, List<String> roleIds,
                              List<String> deniedCapabilityCodes, long version) {}
