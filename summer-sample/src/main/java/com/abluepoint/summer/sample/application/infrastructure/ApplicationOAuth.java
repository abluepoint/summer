package com.abluepoint.summer.sample.application.infrastructure;

import com.abluepoint.summer.sample.application.application.*;
import com.abluepoint.summer.sample.application.domain.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;

public final class ApplicationOAuth {
    private ApplicationOAuth() {}
    public static void validateOauth(OAuthConfigCommand r){ if(r==null)throw new ApplicationValidationException("OAuth config is required"); validateUris(r.redirectUris()); List<String> grants=r.grantTypes()==null?List.of("authorization_code"):r.grantTypes(); if(grants.contains("implicit")||grants.contains("password"))throw new ApplicationValidationException("Unsafe grant type"); if(Boolean.FALSE.equals(r.pkceRequired())&&grants.contains("authorization_code")&&"none".equals(r.clientAuthMethod()))throw new ApplicationValidationException("PKCE is required"); }
    public static void validateUris(List<String> uris){ if(uris==null)return; for(String u:uris){ if(u==null||u.contains("*")||!(u.startsWith("https://")||u.startsWith("http://localhost")))throw new ApplicationValidationException("Redirect URI must be exact and use https"); } }
    public static String secret(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String fingerprint(Object... o){return hash(Arrays.deepToString(o));} public static String op(String e,String k){return e+":"+k;}
}
