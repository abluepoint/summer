package com.abluepoint.summer.sample.enterprise.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Mutation receipts and audit events share the caller's directory transaction. */
@Service
@org.springframework.modulith.NamedInterface("journal")
public class DirectoryJournal {
    private final DirectoryJournalRepository repository;
    private final ObjectMapper mapper;
    public DirectoryJournal(DirectoryJournalRepository repository, ObjectMapper mapper) { this.repository = repository; this.mapper = mapper; }
    @Transactional(readOnly = true)
    public Optional<DirectoryReceipt> find(String enterpriseId, String namespace, String key) {
        return repository.findByEnterpriseIdAndNamespaceAndOperationKey(enterpriseId, namespace, key)
                .map(e -> new DirectoryReceipt(e.fingerprint, e.payload));
    }
    public <T> T decode(DirectoryReceipt receipt, Class<T> type) { return mapper.readValue(receipt.payload(), type); }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void remember(String enterpriseId, String namespace, String key, String fingerprint, Object result) {
        repository.saveAndFlush(new DirectoryJournalEntity(enterpriseId, namespace, key, null, fingerprint, mapper.writeValueAsString(result)));
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void audit(String enterpriseId, String namespace, String resourceId, Object event) {
        repository.saveAndFlush(new DirectoryJournalEntity(enterpriseId, namespace, UUID.randomUUID().toString(), resourceId, null, mapper.writeValueAsString(event)));
    }
    @Transactional(readOnly = true)
    public <T> List<T> audit(String enterpriseId, String namespace, String resourceId, Class<T> type) {
        return repository.findByEnterpriseIdAndNamespaceAndResourceIdOrderById(enterpriseId, namespace, resourceId)
                .stream().map(e -> mapper.readValue(e.payload, type)).toList();
    }

}
