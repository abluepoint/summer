package com.abluepoint.summer.sample.authorization;

import tools.jackson.core.JacksonException;
import com.abluepoint.summer.sample.customer.CustomerAuditService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DataScopeService {
    static final String ENTERPRISE = "ent-acme";
    private static final Set<String> ALLOWED = Set.of("TENANT", "ORGANIZATION", "DEPARTMENT", "ORGANIZATION_TREE", "SELF", "CREATOR", "OWNER");
    private final DataScopePolicyRepository repository;
    private final ObjectMapper mapper;
    private final CustomerAuditService audit;

    DataScopeService(DataScopePolicyRepository repository, ObjectMapper mapper, CustomerAuditService audit) {
        this.repository = repository; this.mapper = mapper; this.audit = audit;
        repository.findByEnterpriseIdAndResource(ENTERPRISE, "system-parameters").orElseGet(() -> repository.save(new DataScopePolicyEntity(UUID.randomUUID().toString(), ENTERPRISE, "system-parameters", "AND", "[{\"type\":\"TENANT\"}]")));
        repository.findByEnterpriseIdAndResource(ENTERPRISE, "dictionaries").orElseGet(() -> repository.save(new DataScopePolicyEntity(UUID.randomUUID().toString(), ENTERPRISE, "dictionaries", "AND", "[{\"type\":\"TENANT\"}]")));
    }

    @Transactional(readOnly = true)
    List<DataScopePolicyView> list(String enterpriseId, Authentication auth) { require(enterpriseId, "enterprise.data-scope.read", auth); return repository.findAll().stream().filter(p -> enterpriseId.equals(p.enterpriseId())).map(this::view).toList(); }
    @Transactional(readOnly = true)
    DataScopePolicyView get(String enterpriseId, String resource, Authentication auth) { require(enterpriseId, "enterprise.data-scope.read", auth); return repository.findByEnterpriseIdAndResource(enterpriseId, resource).map(this::view).orElseThrow(() -> new NoSuchElementException("Data scope policy was not configured")); }
    @Transactional
    DataScopePolicyView put(String enterpriseId, String resource, DataScopePolicyRequest request, Authentication auth) {
        require(enterpriseId, "enterprise.data-scope.write", auth);
        if (request == null || request.rules() == null || request.rules().isEmpty()) throw new IllegalArgumentException("rules are required");
        String op = request.operator() == null ? "AND" : request.operator().toUpperCase(Locale.ROOT);
        if (!Set.of("AND", "OR").contains(op)) throw new IllegalArgumentException("operator must be AND or OR");
        request.rules().forEach(rule -> { if (rule == null || rule.type() == null || !ALLOWED.contains(rule.type().toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("unsupported data scope type"); });
        String json; try { json = mapper.writeValueAsString(request.rules()); } catch (JacksonException e) { throw new IllegalArgumentException("rules cannot be serialized", e); }
        DataScopePolicyEntity entity = repository.findByEnterpriseIdAndResource(enterpriseId, resource).orElseGet(() -> new DataScopePolicyEntity(UUID.randomUUID().toString(), enterpriseId, resource, op, json));
        if (entity.version() != 0 || repository.existsById(entity.policyId())) entity.update(op, json); repository.saveAndFlush(entity); audit.recordResourceSuccess("data-scope:" + enterpriseId + ":" + resource, "DATA_SCOPE_POLICY_UPDATED", null, auth); return view(entity);
    }

    public ScopeFilter filter(String enterpriseId, String resource, Authentication auth) {
        if (!ENTERPRISE.equals(enterpriseId)) throw new AccessDeniedException("Enterprise context mismatch");
        DataScopePolicyEntity entity = repository.findByEnterpriseIdAndResource(enterpriseId, resource).orElseThrow(() -> new AccessDeniedException("Sensitive resource has no data scope policy"));
        List<DataScopeRule> rules; try { rules = mapper.readValue(entity.rulesJson(), mapper.getTypeFactory().constructCollectionType(List.class, DataScopeRule.class)); } catch (JacksonException e) { throw new AccessDeniedException("Data scope policy is invalid"); }
        Set<String> types = rules.stream().map(DataScopeRule::type).filter(Objects::nonNull).map(v -> v.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (types.isEmpty()) throw new AccessDeniedException("Sensitive resource has no data scope policy");
        String subject = auth == null ? null : auth.getName();
        String org = organizationId(auth);
        return new ScopeFilter(types.contains("TENANT"), types.contains("SELF"), types.contains("CREATOR"), types.contains("OWNER"), types.contains("ORGANIZATION") || types.contains("DEPARTMENT") || types.contains("ORGANIZATION_TREE"), "AND".equals(entity.operator()), subject, org);
    }

    public void assertWrite(String enterpriseId, String resource, Authentication auth, String organizationId) {
        ScopeFilter f = filter(enterpriseId, resource, auth);
        if (!f.allows(organizationId, auth == null ? null : auth.getName(), auth == null ? null : auth.getName(), auth == null ? null : auth.getName())) throw new AccessDeniedException("Write target is outside the data scope");
    }
    private DataScopePolicyView view(DataScopePolicyEntity p) { List<DataScopeRule> rules; try { rules = mapper.readValue(p.rulesJson(), mapper.getTypeFactory().constructCollectionType(List.class, DataScopeRule.class)); } catch (JacksonException e) { rules = List.of(); } return new DataScopePolicyView(p.resource(), p.enterpriseId(), p.operator(), rules, p.version(), p.updatedAt()); }
    private void require(String enterpriseId, String capability, Authentication auth) { if (!ENTERPRISE.equals(enterpriseId) || auth == null || !hasMembership(auth, enterpriseId) || !hasCapability(auth, enterpriseId, capability)) throw new AccessDeniedException("The principal is not authorized for the enterprise data scope"); }
    private static boolean hasMembership(Authentication auth, String enterpriseId) { if (!(auth instanceof JwtAuthenticationToken jwt)) return false; Object claim=jwt.getToken().getClaims().get("enterprise_memberships"); return claim instanceof Collection<?> c && c.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(m -> enterpriseId.equals(value(m,"enterpriseId","enterprise_id")) && "ACTIVE".equalsIgnoreCase(value(m,"status","membership_status"))); }
    private static boolean hasCapability(Authentication auth, String enterpriseId, String capability) { if (!(auth instanceof JwtAuthenticationToken jwt)) return false; Object claim=jwt.getToken().getClaims().get("enterprise_capabilities"); if (!(claim instanceof Map<?,?> map)) return false; Object values=map.get(enterpriseId); return values instanceof Collection<?> c && c.stream().map(String::valueOf).anyMatch(capability::equals); }
    private static String value(Map<?,?> m,String a,String b){Object x=m.get(a);if(x==null)x=m.get(b);return x==null?null:x.toString();}
    private static String organizationId(Authentication auth) { if (!(auth instanceof JwtAuthenticationToken jwt)) return null; var c=jwt.getToken().getClaims(); for(String k: List.of("organization_id","organizationId","current_organization_id")){Object v=c.get(k);if(v!=null)return v.toString();} Object context=c.get("organization_context"); if(context instanceof Map<?,?> m){Object v=m.get("organizationId");if(v!=null)return v.toString();} return null; }
    public record ScopeFilter(boolean tenant, boolean self, boolean creator, boolean owner, boolean organization, boolean operatorAnd, String subject, String organizationId) {
        public boolean allows(String rowOrg, String rowCreator, String rowOwner, String rowSubject) { boolean matched=operatorAnd ? true : tenant; if(operatorAnd){ if(tenant) matched &= true; if(self) matched &= Objects.equals(subject,rowSubject); if(creator) matched &= Objects.equals(subject,rowCreator); if(owner) matched &= Objects.equals(subject,rowOwner); if(organization) matched &= organizationId != null && Objects.equals(organizationId,rowOrg); return matched; } if(self) matched |= Objects.equals(subject,rowSubject); if(creator) matched |= Objects.equals(subject,rowCreator); if(owner) matched |= Objects.equals(subject,rowOwner); if(organization) matched |= organizationId != null && Objects.equals(organizationId,rowOrg); return matched; }
    }
}
