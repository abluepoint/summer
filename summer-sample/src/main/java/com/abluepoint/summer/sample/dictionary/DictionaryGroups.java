package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.BusinessDataScopes;
import com.abluepoint.summer.sample.authorization.BusinessDataScopes.DictionaryChild;
import com.abluepoint.summer.sample.configuration.ConfigurationActionAccess;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

record DictionaryDraftGroup(String id, String code, String name, boolean enabled, Boolean everPublished, List<String> memberIds, Map<String,Integer> memberSortOrders) {}
record DictionaryDraftGroupRequest(String id, String code, String name, boolean enabled, List<String> memberIds, List<String> memberOrder, Map<String,java.math.BigDecimal> memberSortOrders) {}
record DictionarySnapshotGroup(String id, String code, String name, boolean enabled, List<String> memberValues, Map<String,Integer> memberSortOrders) {
    DictionarySnapshotGroup {
        memberSortOrders = DictionaryGroups.rankMap(memberValues, memberSortOrders);
    }
}

interface DictionaryGroupRepository extends JpaRepository<DictionaryGroupEntity,String> {
    List<DictionaryGroupEntity> findAllByTenantIdAndDictionaryCodeOrderByCode(String tenant, String code);
    @Query(value="select id from summer_dictionary_group where tenant_id=:tenant and dictionary_code=:code and ever_published=true for update",nativeQuery=true)
    List<String> publishedIds(@Param("tenant") String tenant, @Param("code") String code);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from DictionaryGroupEntity g where g.tenantId=:tenant and g.dictionaryCode=:code order by g.code")
    List<DictionaryGroupEntity> lockAll(@Param("tenant") String tenant,@Param("code") String code);
    @Modifying @Query("update DictionaryGroupEntity g set g.everPublished=true where g.tenantId=:tenant and g.dictionaryCode=:code")
    int markPublished(@Param("tenant") String tenant,@Param("code") String code);
}
interface DictionaryGroupMemberRepository extends JpaRepository<DictionaryGroupMemberEntity,String> {
    List<DictionaryGroupMemberEntity> findAllByTenantIdAndDictionaryCodeOrderBySortOrder(String tenant,String code);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from DictionaryGroupMemberEntity m where m.tenantId=:tenant and m.dictionaryCode=:code order by m.sortOrder")
    List<DictionaryGroupMemberEntity> lockAll(@Param("tenant") String tenant,@Param("code") String code);
}

/** Groups share the caller's aggregate transaction and revision; they never publish separately. */
@Component
class DictionaryGroups {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private final DictionaryGroupRepository groups;
    private final DictionaryGroupMemberRepository members;
    private final ConfigurationActionAccess actions;
    private final BusinessDataScopes scopes;
    DictionaryGroups(DictionaryGroupRepository groups,DictionaryGroupMemberRepository members,ConfigurationActionAccess actions,BusinessDataScopes scopes) {
        this.groups=groups; this.members=members; this.actions=actions; this.scopes=scopes;
    }
    static Map<String,Integer> rankMap(List<String> ids, Map<String,Integer> supplied) {
        var result = new LinkedHashMap<String,Integer>();
        for (int i=0;i<ids.size();i++) result.put(ids.get(i), supplied == null ? i+1 : supplied.getOrDefault(ids.get(i), i+1));
        return Collections.unmodifiableMap(result);
    }
    List<DictionaryDraftGroup> read(DictionaryTypeEntity type, Set<String> visible, boolean current) {
        var rows=current ? groups.lockAll(type.tenantId(),type.code()) : groups.findAllByTenantIdAndDictionaryCodeOrderByCode(type.tenantId(),type.code());
        var links=current ? members.lockAll(type.tenantId(),type.code()) : members.findAllByTenantIdAndDictionaryCodeOrderBySortOrder(type.tenantId(),type.code());
        if(current) { rows.forEach(g -> em.refresh(g,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)); links.forEach(m -> em.refresh(m,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)); }
        var published=current ? new HashSet<>(groups.publishedIds(type.tenantId(),type.code())) : Set.<String>of();
        links.sort(Comparator.comparingInt((DictionaryGroupMemberEntity m) -> m.sortOrder).thenComparing(m -> m.itemId));
        return rows.stream().map(g -> new DictionaryDraftGroup(g.id,g.code,g.name,g.enabled,current ? published.contains(g.id) : g.everPublished,
            links.stream().filter(m -> m.groupId.equals(g.id) && visible.contains(m.itemId)).map(m -> m.itemId).toList(),
            links.stream().filter(m -> m.groupId.equals(g.id) && visible.contains(m.itemId)).collect(Collectors.toMap(m -> m.itemId,m -> m.sortOrder+1)))).toList();
    }
    List<DictionaryDraftGroup> prepare(DictionaryTypeEntity type,List<DictionaryDraftGroupRequest> requested,List<String> order,Authentication auth) {
        var old=read(type,new HashSet<>(order),true);
        if(requested==null) return old;
        var existing=old.stream().collect(Collectors.toMap(DictionaryDraftGroup::id,g->g));
        var ids=new HashSet<String>();var codes=new HashSet<String>();var result=new ArrayList<DictionaryDraftGroup>();
        for(var g:requested) {
            if(g==null || g.id()==null || !ids.add(g.id()) || g.code()==null || !g.code().matches("[a-z][a-z0-9-]{1,78}[a-z0-9]") || !codes.add(g.code()) || g.name()==null || g.name().isBlank() || g.name().length()>200 || g.memberIds()==null || g.memberIds().stream().anyMatch(Objects::isNull) || new HashSet<>(g.memberIds()).size()!=g.memberIds().size() || !order.containsAll(g.memberIds()) || g.memberOrder()==null || g.memberOrder().size()!=g.memberIds().size() || !new HashSet<>(g.memberOrder()).equals(new HashSet<>(g.memberIds()))) throw invalid();
            var prior=existing.get(g.id());
            List<String> next;
            if(prior==null) {
                if(!g.id().startsWith("new:")) throw invalid();
                actions.require("dictionaries","create",auth);
                if(!g.enabled()) actions.require("dictionaries","disable",auth);
                next=order.stream().filter(g.memberIds()::contains).toList();
            } else {
                if(Boolean.TRUE.equals(prior.everPublished()) && !prior.code().equals(g.code())) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
                // New members append; explicit reordering applies only to existing members.
                next=new ArrayList<>(g.memberOrder().stream().filter(prior.memberIds()::contains).toList());
                for(String id:g.memberOrder()) if(!prior.memberIds().contains(id)) next.add(id);
                if(!prior.code().equals(g.code()) || !prior.name().equals(g.name()) || !prior.memberIds().equals(next)) actions.require("dictionaries","update",auth);
                if(prior.enabled()!=g.enabled()) actions.require("dictionaries",g.enabled()?"publish":"disable",auth);
            }
            var ranks = new LinkedHashMap<String,Integer>();
            var previousRanks = prior == null ? List.<Integer>of() : prior.memberSortOrders().values().stream().sorted().toList();
            for (int i=0;i<next.size();i++) {
                long fallback = i < previousRanks.size() ? previousRanks.get(i) : (previousRanks.isEmpty() ? i+1L : (long)previousRanks.getLast()+i-previousRanks.size()+1);
                ranks.put(next.get(i), (int)Math.min(fallback, Integer.MAX_VALUE));
            }
            if (g.memberSortOrders()!=null) {
                for (var entry:g.memberSortOrders().entrySet()) {
                    if (!order.contains(entry.getKey()) || entry.getValue()==null) throw invalid();
                    int rank;
                    try { rank=entry.getValue().intValueExact(); } catch (ArithmeticException failure) { throw invalid(); }
                    if (rank<1) throw invalid();
                    if (ranks.containsKey(entry.getKey())) ranks.put(entry.getKey(),rank);
                }
                next = next.stream().sorted(Comparator.comparingInt((String id) -> ranks.get(id)).thenComparing(id -> id)).toList();
            }
            if (prior!=null && !prior.memberSortOrders().equals(ranks)) actions.require("dictionaries","update",auth);
            result.add(new DictionaryDraftGroup(g.id(),g.code(),g.name(),g.enabled(),prior!=null && Boolean.TRUE.equals(prior.everPublished()),List.copyOf(next),Map.copyOf(ranks)));
        }
        for(var prior:old) if(!ids.contains(prior.id())) {
            actions.require("dictionaries","update",auth);
            if(Boolean.TRUE.equals(prior.everPublished())) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        }
        return result.stream().sorted(Comparator.comparing(DictionaryDraftGroup::code)).toList();
    }
    Set<String> mutationActions(DictionaryTypeEntity type,List<DictionaryDraftGroupRequest> requested) {
        if(requested==null) return Set.of();
        var result=new HashSet<String>();
        if(requested.stream().filter(Objects::nonNull).anyMatch(g -> g.id()!=null && g.id().startsWith("new:"))) result.add("create");
        var keep=requested.stream().filter(Objects::nonNull).map(DictionaryDraftGroupRequest::id).collect(Collectors.toSet());
        if(groups.findAllByTenantIdAndDictionaryCodeOrderByCode(type.tenantId(),type.code()).stream().anyMatch(g -> !keep.contains(g.id))) result.add("delete");
        return result;
    }
    private String parentId(DictionaryTypeEntity type) { return (String)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(type); }
    void save(DictionaryTypeEntity type,List<DictionaryDraftGroup> desired,Map<String,String> itemIds,Map<String,BusinessDataScopes.Filter> ranges) {
        var update=ranges.get("update");
        var oldMembers=members.lockAll(type.tenantId(),type.code());oldMembers.forEach(em::detach);
        scopes.changeDictionaryChildren(update,DictionaryChild.MEMBER,parentId(type),type.tenantId(),type.code(),Map.of(),Map.of());
        var old=groups.lockAll(type.tenantId(),type.code());old.forEach(em::detach);
        var keep=desired.stream().map(DictionaryDraftGroup::id).collect(Collectors.toSet());
        for(var prior:old) if(!keep.contains(prior.id)) {
            if(scopes.changeDictionaryChildren(ranges.get("delete"),DictionaryChild.GROUP,parentId(type),type.tenantId(),type.code(),Map.of("id",prior.id),Map.of())!=1)
                throw new org.springframework.security.access.AccessDeniedException("Dictionary group is outside the delete range");
        }
        var existing=old.stream().collect(Collectors.toMap(g->g.id,g->g));
        for(var g:desired) {
            var row=existing.get(g.id());
            if(row==null) {
                row=new DictionaryGroupEntity(type.tenantId(),type.code(),UUID.randomUUID().toString(),g.code(),g.name(),g.enabled());
                scopes.insertDictionaryChild(ranges.get("create"),DictionaryChild.GROUP,parentId(type),type.tenantId(),type.code(),
                    Map.of("id",row.id,"tenant_id",row.tenantId,"dictionary_code",row.dictionaryCode,"code",g.code(),"name",g.name(),"enabled",g.enabled(),"ever_published",false));
            } else if(scopes.changeDictionaryChildren(update,DictionaryChild.GROUP,parentId(type),type.tenantId(),type.code(),Map.of("id",row.id),
                    Map.of("code",g.code(),"name",g.name(),"enabled",g.enabled()))!=1)
                throw new org.springframework.security.access.AccessDeniedException("Dictionary group is outside the update range");
            for(int i=0;i<g.memberIds().size();i++) {
                String item=itemIds.get(g.memberIds().get(i));if(item==null) throw invalid();
                var link=new DictionaryGroupMemberEntity(type.tenantId(),type.code(),row.id,item,g.memberSortOrders().get(g.memberIds().get(i))-1);
                scopes.insertDictionaryChild(update,DictionaryChild.MEMBER,parentId(type),type.tenantId(),type.code(),
                    Map.of("id",link.id,"tenant_id",link.tenantId,"dictionary_code",link.dictionaryCode,"group_id",link.groupId,"item_id",link.itemId,"sort_order",link.sortOrder));
            }
        }
    }
    void removeItem(DictionaryTypeEntity type,String id,BusinessDataScopes.Filter scope) {
        var old=members.lockAll(type.tenantId(),type.code()).stream().filter(m->m.itemId.equals(id)).toList();old.forEach(em::detach);
        int changed=scopes.changeDictionaryChildren(scope,DictionaryChild.MEMBER,parentId(type),type.tenantId(),type.code(),Map.of("item_id",id),Map.of());
        if(changed!=old.size()) throw new org.springframework.security.access.AccessDeniedException("Dictionary links are outside the delete range");
    }
    List<DictionarySnapshotGroup> snapshot(DictionaryTypeEntity type,List<DictionaryItemEntity> items) {
        var byId=items.stream().collect(Collectors.toMap(DictionaryItemEntity::id,DictionaryItemEntity::value));
        return read(type,byId.keySet(),true).stream().map(g->new DictionarySnapshotGroup(g.id(),g.code(),g.name(),g.enabled(),g.memberIds().stream().map(byId::get).toList(),g.memberIds().stream().collect(Collectors.toMap(byId::get,id -> g.memberSortOrders().get(id))))).toList();
    }
    void markPublished(DictionaryTypeEntity type,BusinessDataScopes.Filter scope) {
        groups.lockAll(type.tenantId(),type.code()).forEach(em::detach);
        scopes.changeDictionaryChildren(scope,DictionaryChild.GROUP,parentId(type),type.tenantId(),type.code(),Map.of(),Map.of("ever_published",true));
    }
    private static SummerException invalid() { return new SummerException(DictionaryErrorCode.INVALID,List.of(new SummerException.Violation("groups","is invalid"))); }
}
