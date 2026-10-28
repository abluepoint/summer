package com.abluepoint.summer.sample.application.application;

import com.abluepoint.summer.sample.application.application.*;
import com.abluepoint.summer.sample.application.domain.*;
import com.abluepoint.summer.sample.application.infrastructure.*;


import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import static com.abluepoint.summer.sample.application.infrastructure.ApplicationOAuth.*;
import static com.abluepoint.summer.sample.application.infrastructure.ApplicationAccess.*;

import java.time.Instant;
import java.util.*;

@Service
public final class ApplicationService {
    private final ApplicationStore store = new ApplicationStore();
    private static final String ENTERPRISE = "ent-acme";

    public synchronized List<ApplicationView> list(String enterpriseId, Authentication a) { requireRead(enterpriseId,a); return store.apps.values().stream().filter(x->x.enterpriseId.equals(enterpriseId)).map(x->view(x,false)).toList(); }
    public synchronized ApplicationView detail(String enterpriseId,String id,Authentication a){ requireRead(enterpriseId,a); return view(app(enterpriseId,id),false); }
    public synchronized ApplicationView create(String enterpriseId, ApplicationCreateCommand req,String key,String rid,Authentication a){
        requireWrite(enterpriseId,a); requireKey(key); String fp=fingerprint(req); ApplicationReceipt p=store.receipts.get(op(enterpriseId,key)); if(p!=null)return replay(p,fp);
        if(req==null||req.name()==null||req.name().isBlank()) throw new ApplicationValidationException("name is required"); validateUris(req.redirectUris());
        if(!ENTERPRISE.equals(enterpriseId)) throw new ApplicationConflictException("Enterprise does not exist");
        String id="app-"+UUID.randomUUID().toString().substring(0,8), clientId="client-"+UUID.randomUUID().toString().substring(0,12), secret=secret();
        ApplicationEntity x=new ApplicationEntity(enterpriseId,id,req.name().trim(),req.description(),"ACTIVE",clientId,new ArrayList<>(req.redirectUris()==null?List.of():req.redirectUris()),new ArrayList<>(),new ApplicationOAuthConfig(List.of("authorization_code"),List.of("code"),List.of("openid"),"none",true,true,new ArrayList<>(req.redirectUris()==null?List.of():req.redirectUris()),1),1);
        x.credentials.add(new ApplicationCredentialEntity("cred-1",1,"ACTIVE",hash(secret),Instant.now(),null)); store.apps.put(id,x); ApplicationView out=view(x,true,secret); remember(enterpriseId,key,fp,out); record(enterpriseId,id,"APPLICATION_CREATED",rid,a); return out;
    }
    public synchronized ApplicationView update(String e,String id,ApplicationUpdateCommand req,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(req,ifm); ApplicationReceipt p=store.receipts.get(op(e,key)); if(p!=null)return replay(p,fp); ApplicationEntity x=app(e,id); etag(x,ifm); if(req==null||req.name()==null||req.name().isBlank())throw new ApplicationValidationException("name is required"); x.name=ApplicationRules.name(req.name()); x.description=req.description(); x.version++; ApplicationView out=view(x,false); remember(e,key,fp,out); record(e,id,"APPLICATION_UPDATED",rid,a); return out; }
    public synchronized ApplicationView configure(String e,String id,OAuthConfigCommand req,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(req,ifm); ApplicationReceipt p=store.receipts.get(op(e,key)); if(p!=null)return replay(p,fp); ApplicationEntity x=app(e,id); etag(x,ifm); validateOauth(req); x.oauth=new ApplicationOAuthConfig(req.grantTypes()==null?List.of("authorization_code"):req.grantTypes(), req.responseTypes()==null?List.of("code"):req.responseTypes(), req.scopes()==null?List.of("openid"):req.scopes(), req.clientAuthMethod()==null?"none":req.clientAuthMethod(), Boolean.TRUE.equals(req.pkceRequired()), req.refreshTokenRotation()==null||req.refreshTokenRotation(), new ArrayList<>(req.redirectUris()==null?x.redirectUris:req.redirectUris()), ++x.version); x.redirectUris=new ArrayList<>(x.oauth.redirectUris()); ApplicationView out=view(x,false); remember(e,key,fp,out); record(e,id,"OAUTH_CONFIG_UPDATED",rid,a); return out; }
    public synchronized ApplicationView setStatus(String e,String id,String status,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(status,ifm); ApplicationReceipt p=store.receipts.get(op(e,key)); if(p!=null)return replay(p,fp); ApplicationEntity x=app(e,id); etag(x,ifm); x.status=status; x.version++; ApplicationView out=view(x,false); remember(e,key,fp,out); record(e,id,"APPLICATION_"+status,rid,a); return out; }
    public synchronized List<CredentialView> credentials(String e,String id,Authentication a){ requireRead(e,a); return app(e,id).credentials.stream().map(c->new CredentialView(c.id,c.version,c.status,c.created.toString(),c.revoked==null?null:c.revoked.toString(),null)).toList(); }
    public synchronized CredentialView rotate(String e,String id,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint("rotate",ifm); ApplicationReceipt p=store.receipts.get(op(e,key)); if(p!=null)return replay(p,fp); ApplicationEntity x=app(e,id); etag(x,ifm); x.credentials.forEach(c->c.status=ApplicationRules.rotatedCredentialStatus(c.status)); int v=x.credentials.size()+1; String secret=secret(); ApplicationCredentialEntity c=new ApplicationCredentialEntity("cred-"+v,v,"ACTIVE",hash(secret),Instant.now(),null); x.credentials.add(c); x.version++; CredentialView out=new CredentialView(c.id,c.version,c.status,c.created.toString(),null,secret); remember(e,key,fp,out); record(e,id,"CREDENTIAL_ROTATED",rid,a); return out; }
    public synchronized CredentialView revoke(String e,String id,String cid,String ifm,String key,String rid,Authentication a){ requireWrite(e,a); requireKey(key); String fp=fingerprint(cid,ifm); ApplicationReceipt p=store.receipts.get(op(e,key)); if(p!=null)return replay(p,fp); ApplicationEntity x=app(e,id); etag(x,ifm); ApplicationCredentialEntity c=x.credentials.stream().filter(v->v.id.equals(cid)).findFirst().orElseThrow(()->new ApplicationConflictException("Credential not found")); c.status="REVOKED"; c.revoked=Instant.now(); x.version++; CredentialView out=new CredentialView(c.id,c.version,c.status,c.created.toString(),c.revoked.toString(),null); remember(e,key,fp,out); record(e,id,"CREDENTIAL_REVOKED",rid,a); return out; }
    public synchronized List<Map<String,Object>> audit(String e,String id,Authentication a){requireRead(e,a); return List.copyOf(store.audit.getOrDefault(e+":"+id,List.of()));}

    private ApplicationEntity app(String e,String id){ if(!ENTERPRISE.equals(e))throw new AccessDeniedException("Enterprise context denied"); ApplicationEntity x=store.apps.get(id); if(x==null||!e.equals(x.enterpriseId))throw new ApplicationConflictException("Application not found"); return x; }
    private void etag(ApplicationEntity x,String h) { ApplicationRules.requireVersion(x.version,h); }
    private void requireKey(String k){if(k==null||k.isBlank())throw new ApplicationValidationException("Idempotency-Key is required");}
    private <T> T replay(ApplicationReceipt p,String fp){if(!p.fp().equals(fp))throw new ApplicationIdempotencyConflictException("Idempotency key reused"); return (T)p.result();}
    private void remember(String e,String k,String fp,Object result){store.receipts.put(op(e,k),new ApplicationReceipt(fp,result));}
    private void record(String e,String id,String action,String rid,Authentication a){store.audit.computeIfAbsent(e+":"+id,k->new ArrayList<>()).add(Map.of("action",action,"requestId",rid==null?"":rid,"principal",a==null?"":String.valueOf(a.getName())));}
    private ApplicationView view(ApplicationEntity x,boolean includeSecret){return view(x,includeSecret,null);} private ApplicationView view(ApplicationEntity x,boolean includeSecret,String secret){return new ApplicationView(x.enterpriseId,x.id,x.name,x.description,x.status,x.clientId,List.copyOf(x.redirectUris),new OAuthConfigView(x.oauth.grants(),x.oauth.responses(),x.oauth.scopes(),x.oauth.auth(),x.oauth.pkce(),x.oauth.rotation(),List.copyOf(x.oauth.redirectUris()),x.oauth.version()),x.credentials.stream().map(c->new CredentialView(c.id,c.version,c.status,c.created.toString(),c.revoked==null?null:c.revoked.toString(),null)).toList(),x.version,includeSecret?secret:null);}
}
