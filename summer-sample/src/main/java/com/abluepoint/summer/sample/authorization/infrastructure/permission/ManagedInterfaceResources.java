package com.abluepoint.summer.sample.authorization.infrastructure.permission;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.application.permission.TrustedPermissionResourceResolver;
import com.abluepoint.summer.sample.authorization.domain.permission.InterfaceMapping;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

/**
 * Authoritative mapping identities shared by discovery, bootstrap, binding and request matching.
 */
@Service
public class ManagedInterfaceResources implements TrustedPermissionResourceResolver {
  private final ObjectProvider<RequestMappingHandlerMapping> mappings;
  private final List<InterfaceRuntimeAdapter> adapters;
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final TransactionTemplate scanTransaction;

  public ManagedInterfaceResources(
      ObjectProvider<RequestMappingHandlerMapping> mappings,
      List<InterfaceRuntimeAdapter> adapters,
      JdbcTemplate db,
      ObjectMapper json,
      PlatformTransactionManager transactions) {
    this.mappings = mappings;
    this.adapters = List.copyOf(adapters);
    this.db = db;
    this.json = json;
    this.scanTransaction = new TransactionTemplate(transactions);
    scanTransaction.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public static InterfaceMapping mvc(
      String method, String path, HandlerMethod handler, RequestMappingInfo info) {
    Map<String, List<String>> conditions = new TreeMap<>();
    conditions.put(
        "declaredMethods",
        info.getMethodsCondition().getMethods().stream().map(Enum::name).toList());
    conditions.put(
        "params",
        info.getParamsCondition().getExpressions().stream().map(Object::toString).toList());
    conditions.put(
        "headers",
        info.getHeadersCondition().getExpressions().stream().map(Object::toString).toList());
    conditions.put(
        "consumes",
        info.getConsumesCondition().getExpressions().stream().map(Object::toString).toList());
    conditions.put(
        "produces",
        info.getProducesCondition().getExpressions().stream().map(Object::toString).toList());
    conditions.put("version", List.of(info.getVersionCondition().toString()));
    boolean custom = info.getCustomCondition() != null;
    if (custom) conditions.put("custom", List.of(info.getCustomCondition().getClass().getName()));
    String owner = handler.getBeanType().getName();
    String domain = com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerRoutes
        .domainKind(owner, path);
    boolean component =
        com.abluepoint.summer.sample.authorization.domain.permission.InterfaceComponentPolicy
                .applicant(owner, method, path)
            || owner.equals("com.abluepoint.summer.sample.identity.SessionController");
    boolean namedBean = handler.getBean() instanceof String;
    return new InterfaceMapping(
        "application",
        "MVC",
        method,
        path,
        conditions,
        owner
            + "@"
            + (namedBean ? handler.getBean() : "unregistered-object")
            + "#"
            + handler.getMethod().toGenericString(),
        domain,
        namedBean && !custom && !component && !domain.equals("COMPONENT"));
  }

  private String hash(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private String locator(InterfaceMapping mapping) {
    return hash(
        json.writeValueAsString(
            List.of(
                mapping.application(),
                mapping.source(),
                mapping.method(),
                mapping.path(),
                mapping.conditions())));
  }

  private String provenance(InterfaceMapping mapping) {
    return hash(
        json.writeValueAsString(
            List.of(mapping.handler(), mapping.domainKind(), mapping.configurable())));
  }

  private List<InterfaceMapping> installed() {
    List<InterfaceMapping> installed = new ArrayList<>();
    mappings
        .orderedStream()
        .forEach(
            mapping ->
                mapping
                    .getHandlerMethods()
                    .forEach(
                        (info, handler) -> {
                          Set<String> methods = new TreeSet<>();
                          info.getMethodsCondition()
                              .getMethods()
                              .forEach(method -> methods.add(method.name()));
                          if (methods.isEmpty())
                            methods.addAll(
                                List.of(
                                    "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS",
                                    "TRACE"));
                          if (methods.contains("GET")) methods.add("HEAD");
                          for (String path : info.getPatternValues())
                            for (String method : methods)
                              installed.add(mvc(method, path, handler, info));
                        }));
    adapters.forEach(adapter -> installed.addAll(adapter.mappings()));
    return installed;
  }

  /** Database lock orders scans across nodes; no scan or revision ever copies associations. */
  public void scan() {
    withScan(() -> null);
  }

  private <T> T withScan(java.util.function.Supplier<T> currentRead) {
    return scanTransaction.execute(
        status -> {
          db.queryForList(
              "select revision from summer_interface_catalog_control where control_id=1 for"
                  + " update");
          var installed = installed();
          Map<String, InterfaceMapping> unique = new TreeMap<>();
          for (var mapping : installed) {
            if (unique.putIfAbsent(locator(mapping), mapping) != null)
              throw new AccessDeniedException("Ambiguous trusted interface locator");
          }
          var rows =
              db.queryForList(
                  "select resource_id,locator_hash,generation,handler_hash,active from"
                      + " summer_interface_resource order by locator_hash,generation");
          Map<String, Map<String, Object>> latest = new HashMap<>();
          rows.forEach(row -> latest.put(row.get("locator_hash").toString(), row));
          boolean changed = false;
          for (var entry : unique.entrySet()) {
            String locator = entry.getKey();
            var mapping = entry.getValue();
            String handlerHash = provenance(mapping);
            var old = latest.get(locator);
            if (old != null
                && Boolean.TRUE.equals(old.get("active"))
                && handlerHash.equals(old.get("handler_hash"))) continue;
            if (old != null)
              db.update(
                  "update summer_interface_resource set active=false where resource_id=?",
                  old.get("resource_id"));
            long generation = old == null ? 1 : ((Number) old.get("generation")).longValue() + 1;
            String id = "interface-" + UUID.randomUUID();
            Map<String, List<String>> safe = new TreeMap<>();
            mapping
                .conditions()
                .forEach(
                    (key, values) ->
                        safe.put(
                            key, values.stream().map(ManagedInterfaceResources::safe).toList()));
            db.update(
                "insert into summer_interface_resource values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                locator,
                generation,
                true,
                handlerHash,
                mapping.application(),
                mapping.source(),
                mapping.method(),
                mapping.path(),
                json.writeValueAsString(safe),
                mapping.handler(),
                mapping.domainKind(),
                mapping.configurable());
            changed = true;
          }
          for (var row : latest.values())
            if (Boolean.TRUE.equals(row.get("active"))
                && !unique.containsKey(row.get("locator_hash").toString())) {
              db.update(
                  "update summer_interface_resource set active=false where resource_id=?",
                  row.get("resource_id"));
              changed = true;
            }
          if (changed)
            db.update(
                "update summer_interface_catalog_control set revision=revision+1 where"
                    + " control_id=1");
          return currentRead.get();
        });
  }

  private static String safe(String expression) {
    int equals = expression.indexOf('=');
    String name = equals < 0 ? expression : expression.substring(0, equals);
    return name.toLowerCase(Locale.ROOT)
            .matches(".*(authorization|cookie|token|secret|password|api.?key).*")
        ? name + "=<redacted>"
        : expression;
  }

  public Resource registered(InterfaceMapping mapping) {
    // Resolve inside the fresh scan transaction, never the bootstrap caller's older MySQL RR
    // snapshot.
    return withScan(
        () -> {
          var rows =
              db.queryForList(
                  "select resource_id,source,handler_hash from summer_interface_resource where"
                      + " locator_hash=? and active=true",
                  locator(mapping));
          if (rows.size() != 1 || !provenance(mapping).equals(rows.getFirst().get("handler_hash")))
            throw new AccessDeniedException("Current interface incarnation required");
          return new Resource(
              rows.getFirst().get("resource_id").toString(), "INTERFACE", mapping.source());
        });
  }

  public Resource actual(HttpServletRequest request, HandlerMethod handler) {
    var candidates =
        mappings
            .orderedStream()
            .flatMap(mapping -> mapping.getHandlerMethods().entrySet().stream())
            .filter(
                entry ->
                    entry.getValue().getMethod().equals(handler.getMethod())
                        && entry.getValue().getBeanType().equals(handler.getBeanType())
                        && entry
                            .getKey()
                            .getPatternValues()
                            .contains(
                                String.valueOf(
                                    request.getAttribute(
                                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)))
                        && entry.getKey().getMatchingCondition(request) != null)
            .toList();
    if (candidates.size() != 1 || candidates.getFirst().getKey().getCustomCondition() != null)
      throw new AccessDeniedException("Complete actual mapping required");
    var info = candidates.getFirst().getKey();
    var registeredHandler = candidates.getFirst().getValue();
    if (registeredHandler.createWithResolvedBean().getBean() != handler.getBean())
      throw new AccessDeniedException("Final handler differs from the registered bean");
    return registered(
        mvc(
            request.getMethod(),
            String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)),
            registeredHandler,
            info));
  }

  public Optional<InterfaceRuntimeAdapter.Match> extension(
      HttpServletRequest request, Object handler) {
    var matches =
        adapters.stream()
            .map(adapter -> adapter.match(request, handler))
            .flatMap(Optional::stream)
            .toList();
    if (matches.size() > 1) throw new AccessDeniedException("Ambiguous extension adapter");
    return matches.stream().findFirst();
  }

  @Override
  public String type() {
    return "INTERFACE";
  }

  @Override
  public void prepare(Domain domain) {
    scan();
  }

  @Override
  public Resource resolve(Domain domain, String id) {
    var rows =
        db.queryForList(
            "select source,domain_kind,configurable from summer_interface_resource where"
                + " resource_id=? and active=true for update",
            id);
    if (rows.size() != 1
        || !Boolean.TRUE.equals(rows.getFirst().get("configurable"))
        || !domain.kind().equals(rows.getFirst().get("domain_kind")))
      throw SummerException.withMessage(
          PermissionErrorCode.INVALID,
          "Active configurable interface in the current domain required");
    return new Resource(id, "INTERFACE", rows.getFirst().get("source").toString());
  }

  public Map<String, Object> directory(Domain domain, int page, int size) {
    if (page < 0 || size < 1 || size > 200)
      throw SummerException.withMessage(PermissionErrorCode.INVALID, "Invalid interface page");
    scan();
    long total =
        db.queryForObject(
            "select count(*) from summer_interface_resource where active=true and configurable=true"
                + " and domain_kind=?",
            Long.class,
            domain.kind());
    var rows =
        db.queryForList(
            "select * from summer_interface_resource where active=true and configurable=true and"
                + " domain_kind=? order by normalized_path,http_method,resource_id limit ? offset"
                + " ?",
            domain.kind(),
            size,
            (long) page * size);
    var items =
        rows.stream()
            .map(
                row ->
                    Map.of(
                        "resourceId",
                        row.get("resource_id"),
                        "application",
                        row.get("application_id"),
                        "source",
                        row.get("source"),
                        "httpMethod",
                        row.get("http_method"),
                        "normalizedPath",
                        row.get("normalized_path"),
                        "conditions",
                        json.readValue(row.get("conditions_json").toString(), Map.class),
                        "generation",
                        row.get("generation")))
            .toList();
    return Map.of("items", items, "total", total, "page", page, "size", size);
  }
}
