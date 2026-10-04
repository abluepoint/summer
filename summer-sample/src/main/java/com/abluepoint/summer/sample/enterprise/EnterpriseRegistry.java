package com.abluepoint.summer.sample.enterprise;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EnterpriseRegistry {
    private final EnterpriseRepository repository;

    public EnterpriseRegistry(EnterpriseRepository repository) {
        this.repository = repository;
        if (!repository.existsByEnterpriseId("ent-acme")) repository.save(new EnterpriseEntity("ent-acme", "Acme Corporation", "acme"));
    }

    @Transactional
    EnterpriseView create(String name, String code) {
        if (name == null || name.isBlank() || code == null || !code.matches("[a-z][a-z0-9-]{2,63}")) {
            throw new IllegalArgumentException("name and code are required; code must be lowercase kebab-case");
        }
        if (repository.existsByCodeIgnoreCase(code)) throw new IllegalStateException("Enterprise code already exists");
        EnterpriseEntity value = repository.save(new EnterpriseEntity("ent-" + UUID.randomUUID(), name.trim(), code));
        return view(value);
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
