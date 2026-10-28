package com.abluepoint.summer.sample.application.infrastructure;

import com.abluepoint.summer.sample.application.application.*;
import java.util.*;
import java.time.Instant;

public final class ApplicationEntity {public String enterpriseId,id,name,description,status,clientId; public List<String> redirectUris; public List<ApplicationCredentialEntity> credentials; public ApplicationOAuthConfig oauth; public long version; public ApplicationEntity(String e,String i,String n,String d,String s,String c,List<String> r,List<ApplicationCredentialEntity> cs,ApplicationOAuthConfig o,long v){enterpriseId=e;id=i;name=n;description=d;status=s;clientId=c;redirectUris=r;credentials=cs;oauth=o;version=v;}}
