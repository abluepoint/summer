package com.abluepoint.summer.sample.authorization.domain;

import java.util.List;

public final class AuthorizationRole { public final String id, enterpriseId; public String name, code, status, bundleId; public final boolean builtIn; public List<String> capabilityIds; public long version; public AuthorizationRole(String i,String e,String n,String c,boolean b,String s,List<String> ids,long v,String bid){id=i;enterpriseId=e;name=n;code=c;builtIn=b;status=s;capabilityIds=ids;version=v;bundleId=bid;} }
