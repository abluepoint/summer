package com.abluepoint.summer.sample.enterprise;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@org.springframework.context.annotation.DependsOn("flywayMigrationInitializer")
public class EnterpriseRegistry {
    private final EnterpriseRepository repository;
    private final DirectoryJournal journal;
    private final org.springframework.context.ApplicationEventPublisher events;

    public EnterpriseRegistry(EnterpriseRepository repository, DirectoryJournal journal, org.springframework.context.ApplicationEventPublisher events) {
        this.repository = repository;
        this.journal = journal;
        this.events = events;
        if (!repository.existsByEnterpriseId("ent-acme")) repository.save(new EnterpriseEntity("ent-acme", "Acme Corporation", "acme"));
    }

    @Transactional
    EnterpriseView create(String name, String code, String creatorSubject, String requestId) {
        if (name == null || name.isBlank() || name.length() > 200 || code == null || !code.matches("[a-z][a-z0-9-]{2,63}")) {
            throw new IllegalArgumentException("name and code are required; code must be lowercase kebab-case");
        }
        if (repository.existsByCodeIgnoreCase(code)) throw new IllegalStateException("Enterprise code already exists");
        if (creatorSubject == null || creatorSubject.isBlank() || creatorSubject.length() > 128) throw new IllegalArgumentException("A valid creator subject is required");
        EnterpriseEntity value = repository.saveAndFlush(new EnterpriseEntity("ent-" + UUID.randomUUID(), name.trim(), code));
        events.publishEvent(new EnterpriseCreated(value.enterpriseId(), creatorSubject, requestId));
        journal.audit(value.enterpriseId(), "platform.enterprise.audit", value.enterpriseId(),
                new PlatformEnterpriseDirectoryService.EnterpriseAudit(creatorSubject, "ENTERPRISE_CREATED", null, "ACTIVE", requestId));
        return view(value);
    }

    @Transactional(readOnly = true)
    List<PlatformEnterpriseDirectoryService.EnterpriseAudit> audit(String enterpriseId) {
        return journal.audit(enterpriseId, "platform.enterprise.audit", enterpriseId, PlatformEnterpriseDirectoryService.EnterpriseAudit.class);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lock(String enterpriseId) {
        repository.lockByEnterpriseId(enterpriseId).orElseThrow(() -> new IllegalArgumentException("Enterprise does not exist"));
    }

    @Transactional(readOnly = true)
    public boolean exists(String enterpriseId) { return repository.existsByEnterpriseId(enterpriseId); }

    @Transactional(readOnly = true)
    List<EnterpriseView> list() { return repository.findAll().stream().map(EnterpriseRegistry::view).toList(); }

    private static EnterpriseView view(EnterpriseEntity value) {
        return new EnterpriseView(value.enterpriseId(), value.name(), value.code(), value.status());
    }

    record EnterpriseView(String enterpriseId, String name, String code, String status) {}
}
