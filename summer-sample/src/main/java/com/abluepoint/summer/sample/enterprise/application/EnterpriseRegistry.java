package com.abluepoint.summer.sample.enterprise.application;

import com.abluepoint.summer.sample.enterprise.infrastructure.DirectoryJournal;
import com.abluepoint.summer.sample.enterprise.infrastructure.EnterpriseEntity;
import com.abluepoint.summer.sample.enterprise.infrastructure.EnterpriseRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@org.springframework.modulith.NamedInterface("directory")
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
    public EnterpriseView create(String name, String code, String creatorSubject, String requestId) {
        com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.validateCreation(name, code);
        if (repository.existsByCodeIgnoreCase(code)) throw new IllegalStateException("Enterprise code already exists");
        com.abluepoint.summer.sample.enterprise.domain.EnterpriseRules.validateCreator(creatorSubject);
        EnterpriseEntity value = repository.saveAndFlush(new EnterpriseEntity("ent-" + UUID.randomUUID(), name.trim(), code));
        events.publishEvent(new EnterpriseCreated(value.enterpriseId(), creatorSubject, requestId));
        journal.audit(value.enterpriseId(), "platform.enterprise.audit", value.enterpriseId(),
                new EnterpriseAuditView(creatorSubject, "ENTERPRISE_CREATED", null, "ACTIVE", requestId));
        return view(value);
    }

    @Transactional(readOnly = true)
    public List<EnterpriseAuditView> audit(String enterpriseId) {
        return journal.audit(enterpriseId, "platform.enterprise.audit", enterpriseId, EnterpriseAuditView.class);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lock(String enterpriseId) {
        repository.lockByEnterpriseId(enterpriseId).orElseThrow(() -> new IllegalArgumentException("Enterprise does not exist"));
    }

    @Transactional(readOnly = true)
    public boolean exists(String enterpriseId) { return repository.existsByEnterpriseId(enterpriseId); }

    @Transactional(readOnly = true)
    public List<EnterpriseView> list() { return repository.findAll().stream().map(EnterpriseRegistry::view).toList(); }

    private static EnterpriseView view(EnterpriseEntity value) {
        return new EnterpriseView(value.enterpriseId(), value.name(), value.code(), value.status());
    }


}
