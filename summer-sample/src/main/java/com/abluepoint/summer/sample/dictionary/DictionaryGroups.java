package com.abluepoint.summer.sample.dictionary;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.configuration.ConfigurationActionAccess;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

record DictionaryDraftGroup(String id, String code, String name, boolean enabled, Boolean everPublished, List<String> memberIds) {}
record DictionaryDraftGroupRequest(String id, String code, String name, boolean enabled, List<String> memberIds, List<String> memberOrder) {}
record DictionarySnapshotGroup(String id, String code, String name, boolean enabled, List<String> memberValues) {}

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
    DictionaryGroups(DictionaryGroupRepository groups,DictionaryGroupMemberRepository members,ConfigurationActionAccess actions) {
        this.groups=groups; this.members=members; this.actions=actions;
    }
    List<DictionaryDraftGroup> read(DictionaryTypeEntity type, Set<String> visible, boolean current) {
        var rows=current ? groups.lockAll(type.tenantId(),type.code()) : groups.findAllByTenantIdAndDictionaryCodeOrderByCode(type.tenantId(),type.code());
        var links=current ? members.lockAll(type.tenantId(),type.code()) : members.findAllByTenantIdAndDictionaryCodeOrderBySortOrder(type.tenantId(),type.code());
        if(current) { rows.forEach(g -> em.refresh(g,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)); links.forEach(m -> em.refresh(m,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)); }
        var published=current ? new HashSet<>(groups.publishedIds(type.tenantId(),type.code())) : Set.<String>of();
        return rows.stream().map(g -> new DictionaryDraftGroup(g.id,g.code,g.name,g.enabled,current ? published.contains(g.id) : g.everPublished,
            links.stream().filter(m -> m.groupId.equals(g.id) && visible.contains(m.itemId)).map(m -> m.itemId).toList())).toList();
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
            result.add(new DictionaryDraftGroup(g.id(),g.code(),g.name(),g.enabled(),prior!=null && Boolean.TRUE.equals(prior.everPublished()),List.copyOf(next)));
        }
        for(var prior:old) if(!ids.contains(prior.id())) {
            actions.require("dictionaries","update",auth);
            if(Boolean.TRUE.equals(prior.everPublished())) throw new SummerException(DictionaryErrorCode.INVALID_STATE);
        }
        return result.stream().sorted(Comparator.comparing(DictionaryDraftGroup::code)).toList();
    }
    void save(DictionaryTypeEntity type,List<DictionaryDraftGroup> desired,Map<String,String> itemIds) {
        members.deleteAll(members.lockAll(type.tenantId(),type.code())); members.flush();
        var old=groups.lockAll(type.tenantId(),type.code());
        var keep=desired.stream().map(DictionaryDraftGroup::id).collect(Collectors.toSet());
        groups.deleteAll(old.stream().filter(g -> !keep.contains(g.id)).toList());groups.flush();
        var existing=old.stream().collect(Collectors.toMap(g->g.id,g->g));
        for(var g:desired) {
            var row=existing.get(g.id());
            if(row==null) row=new DictionaryGroupEntity(type.tenantId(),type.code(),UUID.randomUUID().toString(),g.code(),g.name(),g.enabled());
            row.code=g.code();row.name=g.name();row.enabled=g.enabled();groups.saveAndFlush(row);
            for(int i=0;i<g.memberIds().size();i++) members.save(new DictionaryGroupMemberEntity(type.tenantId(),type.code(),row.id,itemIds.get(g.memberIds().get(i)),i));
        }
        members.flush();
    }
    void removeItem(String tenant,String code,String id) {
        members.deleteAll(members.lockAll(tenant,code).stream().filter(m->m.itemId.equals(id)).toList());members.flush();
    }
    List<DictionarySnapshotGroup> snapshot(DictionaryTypeEntity type,List<DictionaryItemEntity> items) {
        var byId=items.stream().collect(Collectors.toMap(DictionaryItemEntity::id,DictionaryItemEntity::value));
        return read(type,byId.keySet(),true).stream().map(g->new DictionarySnapshotGroup(g.id(),g.code(),g.name(),g.enabled(),g.memberIds().stream().map(byId::get).toList())).toList();
    }
    void markPublished(DictionaryTypeEntity type) { groups.markPublished(type.tenantId(),type.code()); }
    private static SummerException invalid() { return new SummerException(DictionaryErrorCode.INVALID,List.of(new SummerException.Violation("groups","is invalid"))); }
}
