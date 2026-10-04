package com.abluepoint.summer.security.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("summer.security")
public class SummerSecurityProperties {

    private boolean enabled = true;
    private final AuthorizationServer authorizationServer = new AuthorizationServer();
    private final ResourceServer resourceServer = new ResourceServer();
    private final Storage storage = new Storage();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AuthorizationServer getAuthorizationServer() {
        return authorizationServer;
    }

    public ResourceServer getResourceServer() {
        return resourceServer;
    }

    public Storage getStorage() {
        return storage;
    }

    /**
     * Selects the backing implementation for each security store. The
     * {@code memory} implementation is the development-safe default. Set a
     * different implementation name when the application supplies the
     * corresponding Spring Security contract bean.
     */
    public static class Storage {
        private String identity = "memory";
        private String oauthClient = "memory";
        private String authorization = "memory";
        private String consent = "memory";
        private String session = "memory";
        private String keys = "memory";

        public String getIdentity() {
            return identity;
        }

        public void setIdentity(String identity) {
            this.identity = identity;
        }

        public String getOauthClient() {
            return oauthClient;
        }

        public void setOauthClient(String oauthClient) {
            this.oauthClient = oauthClient;
        }

        public String getAuthorization() {
            return authorization;
        }

        public void setAuthorization(String authorization) {
            this.authorization = authorization;
        }

        public String getConsent() {
            return consent;
        }

        public void setConsent(String consent) {
            this.consent = consent;
        }

        public String getSession() {
            return session;
        }

        public void setSession(String session) {
            this.session = session;
        }

        public String getKeys() {
            return keys;
        }

        public void setKeys(String keys) {
            this.keys = keys;
        }
    }

    public static class AuthorizationServer {
        private boolean enabled;
        private String issuer = "http://127.0.0.1:8080";
        private String loginPage = "/login";
        private final User user = new User();
        private List<Client> clients = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getLoginPage() {
            return loginPage;
        }

        public void setLoginPage(String loginPage) {
            this.loginPage = loginPage;
        }

        public User getUser() {
            return user;
        }

        public List<Client> getClients() {
            return clients;
        }

        public void setClients(List<Client> clients) {
            this.clients = clients == null ? new ArrayList<>() : new ArrayList<>(clients);
        }
    }

    public static class ResourceServer {
        private boolean enabled;
        private List<String> protectedPaths = new ArrayList<>(List.of("/api/**"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getProtectedPaths() {
            return protectedPaths;
        }

        public void setProtectedPaths(List<String> protectedPaths) {
            this.protectedPaths = protectedPaths == null ? new ArrayList<>() : new ArrayList<>(protectedPaths);
        }
    }

    public static class User {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Client {
        private String id;
        private String secret;
        private boolean publicClient;
        private String clientAuthenticationMethod;
        private List<String> redirectUris = new ArrayList<>();
        private List<String> postLogoutRedirectUris = new ArrayList<>();
        private List<String> scopes = new ArrayList<>(List.of("openid", "profile"));
        private List<String> grantTypes = new ArrayList<>(List.of("client_credentials"));

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public boolean isPublicClient() {
            return publicClient;
        }

        public void setPublicClient(boolean publicClient) {
            this.publicClient = publicClient;
        }

        public String getClientAuthenticationMethod() {
            return clientAuthenticationMethod;
        }

        public void setClientAuthenticationMethod(String clientAuthenticationMethod) {
            this.clientAuthenticationMethod = clientAuthenticationMethod;
        }

        public List<String> getRedirectUris() {
            return redirectUris;
        }

        public void setRedirectUris(List<String> redirectUris) {
            this.redirectUris = redirectUris == null ? new ArrayList<>() : new ArrayList<>(redirectUris);
        }

        public List<String> getPostLogoutRedirectUris() {
            return postLogoutRedirectUris;
        }

        public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
            this.postLogoutRedirectUris = postLogoutRedirectUris == null ? new ArrayList<>() : new ArrayList<>(postLogoutRedirectUris);
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes == null ? new ArrayList<>() : new ArrayList<>(scopes);
        }

        public List<String> getGrantTypes() {
            return grantTypes;
        }

        public void setGrantTypes(List<String> grantTypes) {
            this.grantTypes = grantTypes == null ? new ArrayList<>() : new ArrayList<>(grantTypes);
        }
    }
}
