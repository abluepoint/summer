package com.abluepoint.summer.sample.application.infrastructure;

import com.abluepoint.summer.sample.application.application.*;
import java.util.*;
import java.time.Instant;

public final class ApplicationCredentialEntity {public String id,status,hash; public int version; public Instant created,revoked; public ApplicationCredentialEntity(String i,int v,String s,String h,Instant c,Instant r){id=i;version=v;status=s;hash=h;created=c;revoked=r;}}
