package com.abluepoint.summer.sample.authorization.domain;

public final class AuthorizationCapability { public final String id, enterpriseId, scope, code, resource, action, risk; public String status; public long version; public AuthorizationCapability(String i, String e, String s, String c, String r, String a, String k, String st, long v) { id=i; enterpriseId=e; scope=s; code=c; resource=r; action=a; risk=k; status=st; version=v; } }
