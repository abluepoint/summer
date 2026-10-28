package com.abluepoint.summer.sample.invitation.infrastructure;
import java.util.*;
import java.time.Instant;

public class JoinApplicationEntity implements InvitationVersionedEntity { public final String id, enterpriseId, applicantEmail, applicantName, organizationId; public final List<String> capabilityCodes; public List<String> materials; public String state = "PENDING", memberId, applicantSubject; public int revision = 1; public JoinApplicationEntity(String id,String e,String email,String name,String org,List<String> caps,List<String> mats){this.id=id;enterpriseId=e;applicantEmail=email;applicantName=name;organizationId=org;capabilityCodes=List.copyOf(caps);materials=new ArrayList<>(mats);} public String etagValue(){return "v"+revision;} public String etag(){return "\""+etagValue()+"\"";} }
