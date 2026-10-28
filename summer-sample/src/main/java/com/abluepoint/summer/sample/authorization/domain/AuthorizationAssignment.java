package com.abluepoint.summer.sample.authorization.domain;

import java.util.List;

public final class AuthorizationAssignment { public final String username, enterpriseId; public final List<String> roleIds, deniedCodes; public long version; public AuthorizationAssignment(String u, String e, List<String> r, List<String> d, long v) { username=u; enterpriseId=e; roleIds=List.copyOf(r); deniedCodes=List.copyOf(d); version=v; } }
