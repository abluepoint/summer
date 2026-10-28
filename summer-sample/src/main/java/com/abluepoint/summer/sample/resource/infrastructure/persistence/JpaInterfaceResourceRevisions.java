package com.abluepoint.summer.sample.resource.infrastructure.persistence;

import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalog;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalogView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDescriptor;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisionNotFoundException;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisionSummaryView;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceRevisions;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceSnapshot;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@org.springframework.stereotype.Repository
public class JpaInterfaceResourceRevisions implements InterfaceResourceRevisions {
    private final InterfaceResourceRevisionRepository repository;
    private final ObjectMapper mapper;

    public JpaInterfaceResourceRevisions(InterfaceResourceRevisionRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override public List<InterfaceResourceCatalogView> all() {
        return repository.findAll().stream().map(this::view).toList();
    }

    public InterfaceResourceCatalogView record(InterfaceResourceCatalog catalog) {
        String json = mapper.writeValueAsString(new InterfaceResourceSnapshot(catalog.items(), catalog.issues()));
        String fingerprint = digest(json);
        var existing = repository.findByFingerprint(fingerprint);
        if (existing.isPresent()) return view(existing.get());
        try {
            return view(repository.saveAndFlush(new InterfaceResourceRevisionEntity(fingerprint, json, catalog.items().size())));
        } catch (DataIntegrityViolationException race) {
            // Concurrent nodes observing identical metadata converge on the immutable fingerprint.
            return repository.findByFingerprint(fingerprint).map(this::view).orElseThrow(() -> race);
        }
    }

    public long count() { return repository.count(); }

    public long latestRevision() {
        return repository.findAllByOrderByRevisionDesc(PageRequest.of(0, 1)).stream()
                .mapToLong(InterfaceResourceRevisionEntity::getRevision).findFirst().orElse(0);
    }

    public List<InterfaceResourceRevisionSummaryView> list(int page, int size) {
        return repository.findAllByOrderByRevisionDesc(PageRequest.of(page, size)).stream()
                .map(InterfaceResourceRevisionEntity::summary).toList();
    }

    public InterfaceResourceCatalogView read(long revision) {
        return repository.findById(revision).map(this::view).orElseThrow(InterfaceResourceRevisionNotFoundException::new);
    }

    private InterfaceResourceCatalogView view(InterfaceResourceRevisionEntity row) {
        if (row.getSnapshotJson().stripLeading().startsWith("[")) {
            var items = mapper.readValue(row.getSnapshotJson(), InterfaceResourceDescriptor[].class);
            return new InterfaceResourceCatalogView(List.of(items), row.getRevision());
        }
        var snapshot = mapper.readValue(row.getSnapshotJson(), InterfaceResourceSnapshot.class);
        return new InterfaceResourceCatalogView(snapshot.items(), row.getRevision(), snapshot.issues());
    }

    static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

}
