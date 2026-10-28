package com.abluepoint.summer.sample.member.infrastructure;
import com.abluepoint.summer.sample.member.application.MemberView;

import com.abluepoint.summer.sample.enterprise.application.EnterpriseRegistry;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persisted membership data. Provisioning callers must establish the authenticated subject explicitly. */
@Service
@org.springframework.modulith.NamedInterface("directory")
public class MemberDirectory {
    private final EnterpriseMemberRepository repository;
    private final EnterpriseRegistry enterprises;
    public MemberDirectory(EnterpriseMemberRepository repository, EnterpriseRegistry enterprises, @org.springframework.beans.factory.annotation.Value("${summer.security.authorization-server.user.username:}") String bootstrapUsername) {
        this.repository = repository; this.enterprises = enterprises;
        seed("member-alice", "alice", "Alice Zhang", "alice@example.test", "ACTIVE");
        seed("member-bob", "bob", "Bob Li", "bob@example.test", "DISABLED");
        if (bootstrapUsername != null && !bootstrapUsername.isBlank() && repository.findByEnterpriseIdAndSubject("ent-acme", bootstrapUsername).isEmpty()) {
            String memberId = "member-bootstrap-" + java.util.UUID.nameUUIDFromBytes(bootstrapUsername.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            repository.save(new EnterpriseMemberEntity("ent-acme", memberId, bootstrapUsername, bootstrapUsername, "", "ACTIVE"));
        }
    }
    @Transactional(readOnly = true)
    public List<MemberView> list(String enterpriseId) {
        return repository.findByEnterpriseIdOrderByMemberId(enterpriseId).stream().map(MemberDirectory::view).toList();
    }
    @Transactional(readOnly = true)
    public Optional<MemberView> find(String enterpriseId, String memberId) {
        return repository.findByEnterpriseIdAndMemberId(enterpriseId, memberId).map(MemberDirectory::view);
    }
    @Transactional(readOnly = true)
    public Optional<MemberView> forSubject(String enterpriseId, String subject) {
        return repository.findByEnterpriseIdAndSubject(enterpriseId, subject).map(MemberDirectory::view);
    }
    @Transactional
    public MemberView provision(String enterpriseId, String memberId, String subject, String displayName, String email) {
        if (!enterprises.exists(enterpriseId)) throw new IllegalArgumentException("Enterprise does not exist");
        com.abluepoint.summer.sample.member.domain.MemberRules.validateProvision(memberId, subject, displayName, email);
        enterprises.lock(enterpriseId);
        var existing = repository.findByEnterpriseIdAndMemberId(enterpriseId, memberId);
        if (existing.isPresent()) {
            var member = existing.get();
            com.abluepoint.summer.sample.member.domain.MemberRules.requireSameSubject(member.subject, subject);
            return view(member);
        }
        if (repository.findByEnterpriseIdAndSubject(enterpriseId, subject).isPresent()) throw new IllegalStateException("Subject already has a membership in this enterprise");
        return view(repository.saveAndFlush(new EnterpriseMemberEntity(enterpriseId, memberId, subject, displayName, email, "ACTIVE")));
    }
    @Transactional
    public void changeStatus(String enterpriseId, String memberId, String status) {
        var member = repository.findByEnterpriseIdAndMemberId(enterpriseId, memberId).orElseThrow();
        member.status = status;
        repository.saveAndFlush(member);
    }
    private void seed(String memberId, String subject, String name, String email, String status) {
        if (repository.findByEnterpriseIdAndMemberId("ent-acme", memberId).isEmpty() && repository.findByEnterpriseIdAndSubject("ent-acme", subject).isEmpty())
            repository.save(new EnterpriseMemberEntity("ent-acme", memberId, subject, name, email, status));
    }
    private static MemberView view(EnterpriseMemberEntity entity) {
        return new MemberView(entity.enterpriseId, entity.memberId, entity.subject, entity.displayName, entity.email, entity.status, entity.version);
    }


}
