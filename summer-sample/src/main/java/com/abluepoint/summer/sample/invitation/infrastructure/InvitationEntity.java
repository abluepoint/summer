package com.abluepoint.summer.sample.invitation.infrastructure;
import java.util.*;
import java.time.Instant;

public class InvitationEntity implements InvitationVersionedEntity { public final String id, enterpriseId, inviteeEmail, organizationId; public final List<String> roleIds, capabilityCodes; public final Instant expiresAt; public String tokenHash, state = "PENDING", memberId; public int revision = 1; public InvitationEntity(String id,String e,String email,String org,List<String> roles,List<String> caps,Instant exp,String hash){this.id=id;enterpriseId=e;inviteeEmail=email;organizationId=org;roleIds=List.copyOf(roles);capabilityCodes=List.copyOf(caps);expiresAt=exp;tokenHash=hash;} public String etagValue(){return "v"+revision;} public String etag(){return "\""+etagValue()+"\"";} }
