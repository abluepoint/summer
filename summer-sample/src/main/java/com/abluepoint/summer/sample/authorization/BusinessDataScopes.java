package com.abluepoint.summer.sample.authorization;

import com.abluepoint.summer.sample.authorization.application.data.DataReadService;
import com.abluepoint.summer.sample.authorization.application.data.DataResourceRegistry;
import com.abluepoint.summer.sample.authorization.application.data.DataOperationExecutor;
import com.abluepoint.summer.sample.authorization.application.data.RegisteredScopeProjection;
import com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Configuration mapping adapter; preset evaluation remains solely in DataReadService. */
@Service
public class BusinessDataScopes {
  private final DataScopeService legacy;
  private final DataReadService reads;
  private final DataResourceRegistry registry;
  private final JdbcTemplate db;
  private final DataOperationExecutor operations;
  private final org.springframework.transaction.support.TransactionTemplate current;
  private final jakarta.persistence.EntityManager entities;
  private record WriteContext(Domain domain, boolean managed, Object transaction) {}
  private final ThreadLocal<WriteContext> writing = new ThreadLocal<>();

  public BusinessDataScopes(DataScopeService legacy, DataReadService reads,
      DataResourceRegistry registry, JdbcTemplate db, DataOperationExecutor operations,
      org.springframework.transaction.PlatformTransactionManager transactions,
      jakarta.persistence.EntityManager entities) {
    this.legacy = legacy;
    this.reads = reads;
    this.registry = registry;
    this.db = db;
    this.operations = operations;
    this.entities = entities;
    current = new org.springframework.transaction.support.TransactionTemplate(transactions);
    current.setReadOnly(true);
    current.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    current.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public <T> T read(String enterprise, String resource, String action, Authentication actor,
      Function<Filter, T> query) {
    return available(() -> {
      for (int attempt = 0; attempt < 2; attempt++) {
        var result = current.execute(tx -> {
          var before = state(enterprise);
          legacy.organization(enterprise, actor);
          T value;
          if ("MANAGED".equals(before.get("engine_mode")))
            value = reads.read(Domain.enterprise(enterprise), resource, action, actor,
                scope -> query.apply(filter(registry.configurationProjection(scope), false)));
          else throw new AccessDeniedException("Unsupported data domain mode");
          return before.equals(state(enterprise)) ? new ReadResult<T>(value) : null;
        });
        if (result != null) return result.value();
      }
      throw new AccessDeniedException("Data authorization changed during business read");
    });
  }

  private record ReadResult<T>(T value) {}
  private Map<String, Object> state(String enterprise) {
    var rows = db.queryForList("select engine_mode,revision from summer_authorization_domain where domain_key=?",
        Domain.enterprise(enterprise).key());
    if (rows.size() != 1) throw new AccessDeniedException("Explicit data domain mode required");
    var state = rows.getFirst();
    if (!(state.get("revision") instanceof Number revision) || revision.longValue() < 0
        || !"MANAGED".equals(state.get("engine_mode")))
      throw new AccessDeniedException("Unconfirmed data domain state");
    return state;
  }

  /** Actual mutations must consume each action's filter inside this one writable domain lock. */
  public <T> T write(String enterprise, String resource, java.util.Set<String> actions,
      Authentication actor, Function<Map<String, Filter>, T> mutation) {
    if (actions == null || actions.isEmpty() || !java.util.Set.of("create", "update", "delete").containsAll(actions))
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode.INVALID,
          "Registered write actions required");
    var fixed = java.util.Set.copyOf(actions);
    return writeResolved(enterprise, resource, () -> fixed, actor, mutation);
  }

  public <T> T writeResolved(String enterprise, String resource,
      java.util.function.Supplier<java.util.Set<String>> resolveActions,
      Authentication actor, Function<Map<String, Filter>, T> mutation) {
    return available(() -> {
      var domain = Domain.enterprise(enterprise);
      var parent = writing.get();
      if (parent != null) {
        if (!parent.domain().equals(domain) || parent.transaction() != transactionResource())
          throw new AccessDeniedException("Same domain and business transaction required for nested write");
        legacy.organization(enterprise, actor);
        var filters = new java.util.TreeMap<String, Filter>();
        var actions = resolveActions.get();
        if (actions == null || actions.isEmpty() || !java.util.Set.of("create", "update", "delete").containsAll(actions))
          throw new AccessDeniedException("Registered nested write actions required");
        for (String action : actions) {
          var filter = reads.read(domain, resource, action, actor,
              scope -> filter(registry.configurationProjection(scope), true));
          filters.put(action, filter);
        }
        return mutation.apply(java.util.Collections.unmodifiableMap(filters));
      }
      return operations.writeResolved(domain, resource, resolveActions, actor, scopes -> {
        legacy.organization(enterprise, actor);
        writing.set(new WriteContext(domain, true, transactionResource()));
        try {
          var projected = new java.util.TreeMap<String, Filter>();
          scopes.forEach((action, scope) -> projected.put(action, filter(registry.configurationProjection(scope), true)));
          return mutation.apply(java.util.Collections.unmodifiableMap(projected));
        } finally { writing.remove(); }
      }, actions -> { throw new AccessDeniedException("Current managed DATA permission required"); });
    });
  }

  public String organization(String enterprise, Authentication actor) { return legacy.organization(enterprise, actor); }
  public void assertOrganization(String enterprise, String organization) { legacy.assertOrganization(enterprise, organization); }
  public static String organizationId(Authentication actor) { return DataScopeService.organizationId(actor); }

  public void assertWrite(String enterprise, String resource, Authentication actor, String organization) {
    assertOrganization(enterprise, organization);
    boolean allowed = read(enterprise, resource, "create", actor,
        scope -> scope.allows(organization, actor == null ? null : actor.getName(), actor == null ? null : actor.getName(), null));
    if (!allowed)
      throw new AccessDeniedException("Write target is outside the data scope");
  }

  private <T> T available(java.util.function.Supplier<T> operation) {
    try { return operation.get(); }
    catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
      throw com.abluepoint.summer.common.error.SummerException.withMessage(
          com.abluepoint.summer.sample.error.SampleTransportErrorCode.DEPENDENCY_UNAVAILABLE,
          "Current business data scope unavailable", failure);
    }
  }

  /** Actual conditional UPDATE; detaching prevents ORM auto-flush from writing ahead of the range. */
  public <T> T update(Filter scope, Class<T> type, T detachedValues, Object id,
      String versionAttribute, long expectedVersion, Map<String, Object> values) {
    writable(scope);
    entities.detach(detachedValues);
    var builder = entities.getCriteriaBuilder();
    var update = builder.createCriteriaUpdate(type);
    var row = update.from(type);
    values.forEach(update::set);
    update.set(versionAttribute, expectedVersion + 1);
    update.where(builder.and(scope.<T>specification().toPredicate(row, null, builder),
        builder.equal(row.get("id"), id), builder.equal(row.get(versionAttribute), expectedVersion)));
    if (entities.createQuery(update).executeUpdate() != 1)
      throw new AccessDeniedException("Update target changed or is outside the action range");
    return entities.find(type, id);
  }

  /** Non-versioned dictionary children serialize through their parent and the domain lock. */
  public <T> T replace(Filter scope, Class<T> type, T detachedValues, Object id, Map<String,Object> values) {
    writable(scope);
    entities.detach(detachedValues);
    var builder = entities.getCriteriaBuilder();
    var update = builder.createCriteriaUpdate(type);
    var row = update.from(type);
    values.forEach(update::set);
    update.where(builder.and(scope.<T>specification().toPredicate(row,null,builder),builder.equal(row.get("id"),id)));
    if (entities.createQuery(update).executeUpdate() != 1)
      throw new AccessDeniedException("Update target is outside the action range");
    return entities.find(type,id);
  }

  /** Compare the original aggregate control values inside the actual ranged UPDATE. */
  public <T> T replaceChecked(Filter scope, Class<T> type, T entity, Object id,
      Map<String,Object> expected, Map<String,Object> values) {
    writable(scope);
    entities.detach(entity);
    var builder = entities.getCriteriaBuilder();
    var update = builder.createCriteriaUpdate(type);
    var row = update.from(type);
    values.forEach(update::set);
    var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
    predicates.add(scope.<T>specification().toPredicate(row,null,builder));
    predicates.add(builder.equal(row.get("id"),id));
    expected.forEach((attribute,value) -> predicates.add(value == null
        ? builder.isNull(row.get(attribute)) : builder.equal(row.get(attribute),value)));
    update.where(builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new)));
    return entities.createQuery(update).executeUpdate() == 1 ? entities.find(type,id) : null;
  }

  /** Outbox retry metadata inherits the actual parameter's UPDATE range. */
  public void updateParameterEvent(Filter scope,String eventId,String tenant,String key,String status,
      java.time.Instant nextAttemptAt,java.time.Instant updatedAt) {
    writable(scope);
    var bindings=new java.util.HashMap<String,Object>(scope.sqlBindings());
    bindings.put("eventId",eventId);bindings.put("eventTenant",tenant);bindings.put("eventKey",key);
    bindings.put("eventStatus",status);bindings.put("eventNext",java.sql.Timestamp.from(nextAttemptAt));
    bindings.put("eventUpdated",java.sql.Timestamp.from(updatedAt));
    int changed=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db).update(
        "update summer_system_parameter_outbox set status=:eventStatus,next_attempt_at=:eventNext,last_error=null,updated_at=:eventUpdated"
        +" where event_id=:eventId and tenant_id=:eventTenant and parameter_key=:eventKey"
        +" and exists (select 1 from summer_system_parameter where tenant_id=:eventTenant and parameter_key=:eventKey and ("
        +scope.sqlPredicate()+"))",bindings);
    if(changed!=1) throw new AccessDeniedException("Parameter event parent is outside the update range");
  }

  public void insertDictionaryVersion(Filter scope, String parentId, Map<String,Object> values) {
    insertHistory(scope,parentId,values,"summer_dictionary_version","summer_dictionary_type",
        java.util.Set.of("version_id","tenant_id","dictionary_code","version_number","status","snapshot_json","created_by","request_id","created_at"),
        " and code=:historyDictionary");
  }

  public <T> void delete(Filter scope, Class<T> type, T entity, Object id) {
    writable(scope);
    entities.detach(entity);
    var builder = entities.getCriteriaBuilder();
    var deletion = builder.createCriteriaDelete(type);
    var row = deletion.from(type);
    deletion.where(builder.and(scope.<T>specification().toPredicate(row,null,builder),builder.equal(row.get("id"),id)));
    if (entities.createQuery(deletion).executeUpdate() != 1)
      throw new AccessDeniedException("Delete target is outside the action range");
  }

  /** Closed physical registrations used only by business code, never request SQL. */
  public enum Table {
    PARAMETERS("summer_system_parameter", "id,tenant_id,organization_id,created_by,owner_id,parameter_key,value_type,value_json,required_flag,min_value,max_value,value_pattern,public_flag,description,status,version,created_at,updated_at"),
    DICTIONARY_TYPES("summer_dictionary_type", "id,tenant_id,organization_id,created_by,owner_id,code,name,published,public_readable,enabled,published_version,draft_revision,effective_revision"),
    DICTIONARY_ITEMS("summer_dictionary_item", "id,tenant_id,organization_id,created_by,owner_id,dictionary_code,item_value,label,sort_order,enabled,description,ever_published"),
    OVERRIDES("summer_configuration_override", "id,tenant_id,resource,config_key,scope_id,organization_id,created_by,owner_id,value_json,public_readable,enabled,version,latest_revision,published_revision,updated_at");
    private final String physical;
    private final java.util.Set<String> columns;
    Table(String physical, String columns) { this.physical = physical; this.columns = java.util.Set.of(columns.split(",")); }
  }

  /** INSERT SELECT applies the same evaluated range to trusted server-constructed ownership. */
  public void insert(Filter scope, Table table, Map<String, Object> values) {
    writable(scope);
    if (!table.columns.containsAll(values.keySet())
        || !values.keySet().containsAll(java.util.Set.of("id", "tenant_id", "owner_id", "created_by", "organization_id")))
      throw new IllegalArgumentException("Registered insert columns and ownership required");
    var bindings = new java.util.HashMap<String, Object>(scope.sqlBindings());
    var columns = new java.util.TreeSet<>(values.keySet());
    var selections = new java.util.ArrayList<String>();
    int index = 0;
    for (String column : columns) {
      String key = "insertValue" + index++;
      bindings.put(key, values.get(column));
      selections.add(":" + key);
    }
    bindings.put("candidateTenant", values.get("tenant_id"));
    bindings.put("candidateOwner", values.get("owner_id"));
    bindings.put("candidateCreator", values.get("created_by"));
    bindings.put("candidateDepartment", values.get("organization_id"));
    String candidate = "(select cast(:candidateTenant as char(80)) tenant_id,"
        + "cast(:candidateOwner as char(128)) owner_id,cast(:candidateCreator as char(128)) created_by,"
        + "cast(:candidateDepartment as char(64)) organization_id) candidate";
    int changed = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db).update(
        "insert into " + table.physical + " (" + String.join(",", columns) + ") select "
            + String.join(",", selections) + " from " + candidate + " where " + scope.sqlPredicate(), bindings);
    if (changed != 1) throw new AccessDeniedException("Create candidate is outside the action range");
  }

  private Object transactionResource() {
    return org.springframework.transaction.support.TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
  }

  private Filter filter(RegisteredScopeProjection projection, boolean writable) {
    return new Filter(projection,writable,writable ? writing.get() : null);
  }

  private void writable(Filter scope) {
    if (scope == null || !scope.writable || scope.owner == null || scope.owner != writing.get()
        || scope.owner.transaction() == null || scope.owner.transaction() != transactionResource()
        || org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        || !org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
      throw new AccessDeniedException("Action range inside a writable business transaction required");
  }

  /** Child history INSERT SELECTs retain the real parent's action range. */
  public void insertParameterVersion(Filter scope, String parentId, Map<String, Object> values) {
    insertHistory(scope,parentId,values,"summer_system_parameter_version","summer_system_parameter",
        java.util.Set.of("id","tenant_id","parameter_key","revision","state","snapshot_json","base_version","idempotency_key","principal","created_at"),
        " and parameter_key=:historyKey");
  }

  public void insertOverrideRevision(Filter scope, String parentId, Map<String, Object> values) {
    if (!parentId.equals(values.get("override_id"))) throw new IllegalArgumentException("Actual override parent required");
    insertHistory(scope,parentId,values,"summer_configuration_override_revision","summer_configuration_override",
        java.util.Set.of("id","override_id","tenant_id","revision","state","value_json","public_readable","enabled","principal","request_id","created_at"), "");
  }

  private void insertHistory(Filter scope, String parentId, Map<String,Object> values, String child,
      String parent, java.util.Set<String> allowed, String parentConstraint) {
    writable(scope);
    if (!allowed.equals(values.keySet())) throw new IllegalArgumentException("Registered history columns required");
    var bindings = new java.util.HashMap<String, Object>(scope.sqlBindings());
    var columns = new java.util.TreeSet<>(values.keySet());
    var selections = new java.util.ArrayList<String>();
    int index = 0;
    for (String column : columns) {
      String key = "historyValue" + index++;
      bindings.put(key, values.get(column)); selections.add(":" + key);
    }
    bindings.put("parentId",parentId); bindings.put("parentTenant",values.get("tenant_id"));
    if (values.containsKey("parameter_key")) bindings.put("historyKey",values.get("parameter_key"));
    if (values.containsKey("dictionary_code")) bindings.put("historyDictionary",values.get("dictionary_code"));
    int changed = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db).update(
        "insert into " + child + " (" + String.join(",",columns) + ") select " + String.join(",",selections)
            + " from " + parent + " where id=:parentId and tenant_id=:parentTenant" + parentConstraint
            + " and (" + scope.sqlPredicate() + ")",bindings);
    if (changed != 1) throw new AccessDeniedException("History parent is outside the action range");
  }

  /** These children have no ownership columns: their real dictionary parent defines the range. */
  public enum DictionaryChild {
    GROUP("summer_dictionary_group", "id,tenant_id,dictionary_code,code,name,enabled,ever_published"),
    MEMBER("summer_dictionary_group_member", "id,tenant_id,dictionary_code,group_id,item_id,sort_order");
    private final String table;
    private final java.util.Set<String> columns;
    DictionaryChild(String table,String columns) { this.table=table;this.columns=java.util.Set.of(columns.split(",")); }
  }
  private java.util.Map<String,Object> dictionaryParent(Filter scope,String parentId,String tenant,String code) {
    writable(scope);
    var bindings = new java.util.HashMap<String,Object>(scope.sqlBindings());
    bindings.put("dictionaryParent",parentId);bindings.put("dictionaryTenant",tenant);bindings.put("dictionaryCode",code);
    return bindings;
  }
  private String dictionaryParentPredicate(Filter scope) {
    return "exists (select 1 from summer_dictionary_type where id=:dictionaryParent"
        + " and tenant_id=:dictionaryTenant and code=:dictionaryCode and ("+scope.sqlPredicate()+"))";
  }
  public void insertDictionaryChild(Filter scope,DictionaryChild child,String parentId,String tenant,String code,Map<String,Object> values) {
    var bindings=dictionaryParent(scope,parentId,tenant,code);
    if (!child.columns.equals(values.keySet()) || !tenant.equals(values.get("tenant_id")) || !code.equals(values.get("dictionary_code")))
      throw new IllegalArgumentException("Exact registered child columns and actual parent required");
    var columns = new java.util.TreeSet<>(values.keySet());
    var selections = new java.util.ArrayList<String>();int index=0;
    for(String column:columns) { String key="childValue"+index++;bindings.put(key,values.get(column));selections.add(":"+key); }
    int changed=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db).update(
        "insert into "+child.table+" ("+String.join(",",columns)+") select "+String.join(",",selections)
        +" where "+dictionaryParentPredicate(scope),bindings);
    if(changed!=1) throw new AccessDeniedException("Dictionary child parent is outside the action range");
  }
  public int changeDictionaryChildren(Filter scope,DictionaryChild child,String parentId,String tenant,String code,
      Map<String,Object> identity,Map<String,Object> values) {
    var bindings=dictionaryParent(scope,parentId,tenant,code);
    if(!java.util.Set.of("id","group_id","item_id").containsAll(identity.keySet()) || !child.columns.containsAll(identity.keySet())
        || !child.columns.containsAll(values.keySet()) || values.keySet().stream().anyMatch(java.util.Set.of("id","tenant_id","dictionary_code")::contains))
      throw new IllegalArgumentException("Registered child identity and mutable columns required");
    var conditions=new java.util.ArrayList<String>();int index=0;
    conditions.add("tenant_id=:dictionaryTenant");conditions.add("dictionary_code=:dictionaryCode");
    for(var value:identity.entrySet()) {String key="childIdentity"+index++;bindings.put(key,value.getValue());conditions.add(value.getKey()+"=:"+key);}
    conditions.add(dictionaryParentPredicate(scope));
    var changes=new java.util.ArrayList<String>();
    for(var value:values.entrySet()) {String key="childChange"+index++;bindings.put(key,value.getValue());changes.add(value.getKey()+"=:"+key);}
    return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db).update(
        (values.isEmpty() ? "delete from "+child.table : "update "+child.table+" set "+String.join(",",changes))
        +" where "+String.join(" and ",conditions),bindings);
  }

  public static final class Filter {
    private final RegisteredScopeProjection projection;
    private final boolean writable;
    private final WriteContext owner;
    private Filter(RegisteredScopeProjection projection, boolean writable, WriteContext owner) {
      if(projection==null)throw new AccessDeniedException("Current managed DATA projection required");
      this.projection=projection; this.writable=writable; this.owner=owner;
    }
    public boolean allows(String organization, String creator, String owner, String subject) {
      return projection.allows(organization, owner);
    }
    public <T> Specification<T> specification() { return projection.specification(); }
    public String sqlPredicate() { return projection.predicate(); }
    public Map<String, Object> sqlBindings() { return projection.bindings(); }
    public String fingerprint() { return projection.fingerprint(); }
  }
}
