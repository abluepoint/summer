package com.abluepoint.summer.sample.application;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
final class ApplicationService {
    private static final String ENTERPRISE = "ent-acme";
    private final Map<String, AppRecord> apps = new LinkedHashMap<>();
    private final Map<String, Receipt> receipts = new HashMap<>();
    private final Map<String, List<Map<String,Object>>> audit = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    synchronized List<ApplicationView> list(String enterpriseId, Authentication a) { requireRead(enterpriseId,a); return apps.values().stream().filter(x->x.enterpriseId.equals(enterpriseId)).map(x->view(x,false)).toList(); }
    synchronized ApplicationView detail(String enterpriseId,String id,Authentication a){ requireRead(enterpriseId,a); return view(app(enterpriseId,id),false); }
    synchronized ApplicationView create(String enterpriseId, ApplicationCreateRequest req,String key,String rid,Authentication a){
        requireWrite(enterpriseId,a); requireKey(key); String fp=fingerprint(req); Receipt p=receipts.get(op(enterpriseId,key)); if(p!=null)return replay(p,fp);
        if(req==null||req.name()==null||req.name().isBlank()) throw new ApplicationValidationException("name is required"); validateUris(req.redirectUris());
        if(!ENTERPRISE.equals(enterpriseId)) throw new ApplicationConflictException("Enterprise does not exist");
        String id="app-"+UUID.randomUUID().toString().substring(0,8), clientId="client-"+UUID.randomUUID().toString().substring(0,12), secret=secret();
        AppRecord x=new AppRecord(enterpriseId,id,req.name().trim(),req.description(),"ACTIVE",clientId,new ArrayList<>(req.redirectUris()==null?List.of():req.redirectUris()),new ArrayList<>(),new OAuthConfig(List.of("authorization_code"),List.of("code"),List.of("openid"),"none",true,true,new ArrayList<>(req.redirectUris()==null?List.of():req.redirectUris()),1),1);
        x.credentials.add(new Cred("cred-1",1,"ACTIVE",hash(secret),Instant.now(),null)); apps.put(id,x); ApplicationView out=view(x,true,secret); remember(enterpriseId,key,fp,out); record(enterpriseId,id,"APPLICATION_CREATED",rid,a); return out;
    }
    synchronized ApplicationView update(String e,String id,ApplicationUpdateRequest req,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(req,ifm); Receipt p=receipts.get(op(e,key)); if(p!=null)return replay(p,fp); AppRecord x=app(e,id); etag(x,ifm); if(req==null||req.name()==null||req.name().isBlank())throw new ApplicationValidationException("name is required"); x.name=req.name().trim(); x.description=req.description(); x.version++; ApplicationView out=view(x,false); remember(e,key,fp,out); record(e,id,"APPLICATION_UPDATED",rid,a); return out; }
    synchronized ApplicationView configure(String e,String id,OAuthConfigRequest req,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(req,ifm); Receipt p=receipts.get(op(e,key)); if(p!=null)return replay(p,fp); AppRecord x=app(e,id); etag(x,ifm); validateOauth(req); x.oauth=new OAuthConfig(req.grantTypes()==null?List.of("authorization_code"):req.grantTypes(), req.responseTypes()==null?List.of("code"):req.responseTypes(), req.scopes()==null?List.of("openid"):req.scopes(), req.clientAuthMethod()==null?"none":req.clientAuthMethod(), Boolean.TRUE.equals(req.pkceRequired()), req.refreshTokenRotation()==null||req.refreshTokenRotation(), new ArrayList<>(req.redirectUris()==null?x.redirectUris:req.redirectUris()), ++x.version); x.redirectUris=new ArrayList<>(x.oauth.redirectUris); ApplicationView out=view(x,false); remember(e,key,fp,out); record(e,id,"OAUTH_CONFIG_UPDATED",rid,a); return out; }
    synchronized ApplicationView setStatus(String e,String id,String status,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(status,ifm); Receipt p=receipts.get(op(e,key)); if(p!=null)return replay(p,fp); AppRecord x=app(e,id); etag(x,ifm); x.status=status; x.version++; ApplicationView out=view(x,false); remember(e,key,fp,out); record(e,id,"APPLICATION_"+status,rid,a); return out; }
    synchronized List<CredentialView> credentials(String e,String id,Authentication a){ requireRead(e,a); return app(e,id).credentials.stream().map(c->new CredentialView(c.id,c.version,c.status,c.created.toString(),c.revoked==null?null:c.revoked.toString(),null)).toList(); }
    synchronized CredentialView rotate(String e,String id,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint("rotate",ifm); Receipt p=receipts.get(op(e,key)); if(p!=null)return replay(p,fp); AppRecord x=app(e,id); etag(x,ifm); x.credentials.stream().filter(c->c.status.equals("ACTIVE")).forEach(c->c.status="GRACE"); int v=x.credentials.size()+1; String secret=secret(); Cred c=new Cred("cred-"+v,v,"ACTIVE",hash(secret),Instant.now(),null); x.credentials.add(c); x.version++; CredentialView out=new CredentialView(c.id,c.version,c.status,c.created.toString(),null,secret); remember(e,key,fp,out); record(e,id,"CREDENTIAL_ROTATED",rid,a); return out; }
    synchronized CredentialView revoke(String e,String id,String cid,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(cid,ifm); Receipt p=receipts.get(op(e,key)); if(p!=null)return replay(p,fp); AppRecord x=app(e,id); etag(x,ifm); Cred c=x.credentials.stream().filter(v->v.id.equals(cid)).findFirst().orElseThrow(()->new ApplicationConflictException("Credential not found")); c.status="REVOKED"; c.revoked=Instant.now(); x.version++; CredentialView out=new CredentialView(c.id,c.version,c.status,c.created.toString(),c.revoked.toString(),null); remember(e,key,fp,out); record(e,id,"CREDENTIAL_REVOKED",rid,a); return out; }
    synchronized List<Map<String,Object>> audit(String e,String id,Authentication a){requireRead(e,a); return List.copyOf(audit.getOrDefault(e+":"+id,List.of()));}

    private void validateOauth(OAuthConfigRequest r){ if(r==null)throw new ApplicationValidationException("OAuth config is required"); validateUris(r.redirectUris()); List<String> grants=r.grantTypes()==null?List.of("authorization_code"):r.grantTypes(); if(grants.contains("implicit")||grants.contains("password"))throw new ApplicationValidationException("Unsafe grant type"); if(Boolean.FALSE.equals(r.pkceRequired())&&grants.contains("authorization_code")&&"none".equals(r.clientAuthMethod()))throw new ApplicationValidationException("PKCE is required"); }
    private void validateUris(List<String> uris){ if(uris==null)return; for(String u:uris){ if(u==null||u.contains("*")||!(u.startsWith("https://")||u.startsWith("http://localhost")))throw new ApplicationValidationException("Redirect URI must be exact and use https"); } }
    private AppRecord app(String e,String id){ if(!ENTERPRISE.equals(e))throw new AccessDeniedException("Enterprise context denied"); AppRecord x=apps.get(id); if(x==null||!e.equals(x.enterpriseId))throw new ApplicationConflictException("Application not found"); return x; }
    private void requireRead(String e,Authentication a){ require(e,a,"enterprise.application.read","enterprise.applications.read"); }
    private void requireWrite(String e,Authentication a){ require(e,a,"enterprise.application.write","enterprise.applications.write"); }
    private void require(String e,Authentication a,String... caps){ if(!(a instanceof JwtAuthenticationToken jwt)||!active(jwt,e)||!cap(jwt,e,caps)||jwt.getAuthorities().stream().noneMatch(x->Arrays.stream(caps).anyMatch(c->("SCOPE_"+c).equals(x.getAuthority()))))throw new AccessDeniedException("Application capability denied"); }
    private boolean active(JwtAuthenticationToken j,String e){ Object c=j.getToken().getClaims().get("enterprise_memberships"); return c instanceof Collection<?> v&&v.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(m->e.equals(String.valueOf(m.getOrDefault("enterpriseId",m.get("enterprise_id"))))&&"ACTIVE".equalsIgnoreCase(String.valueOf(m.getOrDefault("status",m.get("membership_status"))))); }
    private boolean cap(JwtAuthenticationToken j,String e,String... caps){ Object c=j.getToken().getClaims().get("enterprise_capabilities"); if(!(c instanceof Map<?,?> m))return false; Object v=m.get(e); return v instanceof Collection<?> col&&col.stream().map(String::valueOf).anyMatch(s->Arrays.asList(caps).contains(s)); }
    private void etag(AppRecord x,String h){ if(h==null||!h.equals("\""+x.version+"\""))throw new ApplicationPreconditionException("The resource has changed"); }
    private void requireKey(String k){if(k==null||k.isBlank())throw new ApplicationValidationException("Idempotency-Key is required");}
    private String secret(){byte[] b=new byte[24];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String fingerprint(Object... o){return hash(Arrays.deepToString(o));} private static String op(String e,String k){return e+":"+k;}
    private <T> T replay(Receipt p,String fp){if(!p.fp.equals(fp))throw new ApplicationIdempotencyConflictException("Idempotency key reused"); return (T)p.result;}
    private void remember(String e,String k,String fp,Object result){receipts.put(op(e,k),new Receipt(fp,result));}
    private void record(String e,String id,String action,String rid,Authentication a){audit.computeIfAbsent(e+":"+id,k->new ArrayList<>()).add(Map.of("action",action,"requestId",rid==null?"":rid,"principal",a==null?"":String.valueOf(a.getName())));}
    private ApplicationView view(AppRecord x,boolean includeSecret){return view(x,includeSecret,null);} private ApplicationView view(AppRecord x,boolean includeSecret,String secret){return new ApplicationView(x.enterpriseId,x.id,x.name,x.description,x.status,x.clientId,List.copyOf(x.redirectUris),new OAuthConfigView(x.oauth.grants,x.oauth.responses,x.oauth.scopes,x.oauth.auth,x.oauth.pkce,x.oauth.rotation,List.copyOf(x.oauth.redirectUris),x.oauth.version),x.credentials.stream().map(c->new CredentialView(c.id,c.version,c.status,c.created.toString(),c.revoked==null?null:c.revoked.toString(),null)).toList(),x.version,includeSecret?secret:null);}
    private static final class AppRecord {String enterpriseId,id,name,description,status,clientId; List<String> redirectUris; List<Cred> credentials; OAuthConfig oauth; long version; AppRecord(String e,String i,String n,String d,String s,String c,List<String> r,List<Cred> cs,OAuthConfig o,long v){enterpriseId=e;id=i;name=n;description=d;status=s;clientId=c;redirectUris=r;credentials=cs;oauth=o;version=v;}}
    private static final class Cred {String id,status,hash; int version; Instant created,revoked; Cred(String i,int v,String s,String h,Instant c,Instant r){id=i;version=v;status=s;hash=h;created=c;revoked=r;}}
    private record OAuthConfig(List<String> grants,List<String> responses,List<String> scopes,String auth,boolean pkce,boolean rotation,List<String> redirectUris,long version){}
    private record Receipt(String fp,Object result){}
}
