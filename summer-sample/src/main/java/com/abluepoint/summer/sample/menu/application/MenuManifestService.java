package com.abluepoint.summer.sample.menu.application;

import com.abluepoint.summer.sample.menu.application.command.MenuManifestOverrideCommand;
import com.abluepoint.summer.sample.menu.application.command.MenuManifestSyncCommand;
import com.abluepoint.summer.sample.menu.application.view.MenuManifestItemView;
import com.abluepoint.summer.sample.menu.application.view.MenuManifestView;
import com.abluepoint.summer.sample.menu.domain.MenuManifestItem;
import com.abluepoint.summer.sample.menu.domain.exception.MenuManifestConflictException;
import com.abluepoint.summer.sample.menu.domain.exception.MenuManifestPreconditionException;
import com.abluepoint.summer.sample.menu.domain.exception.MenuManifestValidationException;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestEntity;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestOverrideEntity;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestOverrideRepository;
import com.abluepoint.summer.sample.menu.infrastructure.persistence.MenuManifestRepository;

import com.abluepoint.summer.sample.authorization.application.AuthorizationService;
import com.abluepoint.summer.sample.authorization.application.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.application.AuthorizationAuditStore;
import com.abluepoint.summer.mvc.request.api.RequestIdAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.abluepoint.summer.sample.menu.domain.MenuManifestRules;
import com.abluepoint.summer.sample.menu.domain.MenuManifestRules.Effective;
import static com.abluepoint.summer.sample.menu.domain.MenuManifestRules.*;

@Service
public class MenuManifestService {
    private final MenuManifestRepository manifestRepository;
    private final MenuManifestOverrideRepository overrideRepository;
    private final AuthorizationService authorizationService;
    private final AuthorizationChangeEventService changeEvents;
    private final RequestIdAccessor requestId;
    private final AuthorizationAuditStore auditStore;
    private final Map<String, Receipt> receipts = new ConcurrentHashMap<>();

    public MenuManifestService(MenuManifestRepository manifestRepository, MenuManifestOverrideRepository overrideRepository,
                        AuthorizationService authorizationService, AuthorizationChangeEventService changeEvents,
                        RequestIdAccessor requestId, AuthorizationAuditStore auditStore) {
        this.manifestRepository = manifestRepository;
        this.overrideRepository = overrideRepository;
        this.authorizationService = authorizationService;
        this.changeEvents = changeEvents;
        this.requestId = requestId;
        this.auditStore = auditStore;
    }

    @Transactional(readOnly = true)
    public synchronized MenuManifestView current(Authentication authentication) {
        authorizationService.requirePlatformMenu(authentication);
        List<MenuManifestEntity> items = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
        long version = items.stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0);
        return view(version, items, Map.of(), null);
    }

    @Transactional
    public synchronized MenuManifestView sync(MenuManifestSyncCommand request, String key, Authentication authentication) {
        authorizationService.requirePlatformMenu(authentication);
        if (key == null || key.isBlank()) throw new MenuManifestValidationException("Idempotency-Key is required");
        MenuManifestRules.validate(request == null ? 0 : request.manifestVersion(), request == null ? null : request.items());
        String fingerprint = manifestFingerprint(request);
        Receipt previous = receipts.get(key);
        if (previous != null) {
            if (!previous.fingerprint.equals(fingerprint)) throw new MenuManifestConflictException("Idempotency key reused");
            return (MenuManifestView) previous.result;
        }
        long current = manifestRepository.findAll().stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0);
        MenuManifestRules.requireCurrentVersion(request.manifestVersion(), current);
        if (request.manifestVersion() == current) {
            List<MenuManifestEntity> existing = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
            MenuManifestView replay = view(current, existing, Map.of(), null);
            if (manifestFingerprint(request).equals(manifestFingerprint(toRequest(replay)))) return replay;
            throw new MenuManifestConflictException("Manifest version already contains different metadata");
        }
        manifestRepository.deleteAll();
        List<MenuManifestEntity> entities = request.items().stream().map(item -> new MenuManifestEntity(item, request.manifestVersion())).toList();
        manifestRepository.saveAll(entities);
        MenuManifestView result = view(request.manifestVersion(), entities, Map.of(), null);
        rememberAfterCommit(key, new Receipt(fingerprint, result));
        auditStore.mutation("ent-acme", "manifest", "MENU_MANIFEST_SYNCED", requestId.currentRequestId(), authentication.getName());
        changeEvents.enqueue("ent-acme", "MENU_MANIFEST", "manifest", "MENU_MANIFEST_SYNCED", request.manifestVersion(), requestId.currentRequestId());
        return result;
    }

    public synchronized MenuManifestView visible(String enterpriseId, Authentication authentication) {
        authorizationService.requireEnterpriseMembership(enterpriseId, authentication);
        List<MenuManifestEntity> items = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
        Map<String, MenuManifestOverrideEntity> overrides = new HashMap<>();
        overrideRepository.findAllByEnterpriseId(enterpriseId).forEach(item -> overrides.put(item.menuId(), item));
        List<MenuManifestItemView> visible = new ArrayList<>();
        long version = items.stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0);
        for (MenuManifestEntity item : items) {
            MenuManifestOverrideEntity override = overrides.get(item.menuId());
            Effective effective = MenuManifestRules.effective(item.parentMenuId(), item.sortOrder(), item.capabilityCode(), override == null ? null : override.enabled(), override == null ? null : override.parentMenuId(), override == null ? null : override.sortOrder(), override == null ? null : override.capabilityCode());
            if (!effective.enabled() || !allowed(effective.capabilityCode(), authentication, enterpriseId)) continue;
            List<String> actions = actions(item.menuId(), effective.capabilityCode(), authentication, enterpriseId);
            if (!MenuManifestRules.visible(item.menuId(), actions)) continue;
            visible.add(new MenuManifestItemView(item.menuId(), effective.parentMenuId(), item.path(), item.titleKey(),
                    item.iconKey(), effective.sortOrder(), effective.capabilityCode(), true, actions,
                    override == null ? 1 : override.version()));
        }
        Map<String, String> parents = new HashMap<>();
        Set<String> visibleIds = new HashSet<>();
        visible.forEach(item -> { visibleIds.add(item.menuId()); parents.put(item.menuId(), item.parentMenuId()); });
        visible.removeIf(item -> !MenuManifestRules.visibleAncestors(item.parentMenuId(), parents, visibleIds));
        visible.sort((left, right) -> MenuManifestRules.compare(left.sortOrder(), left.menuId(), right.sortOrder(), right.menuId()));
        return new MenuManifestView(version, List.copyOf(visible));
    }

    public synchronized MenuManifestView management(String enterpriseId, Authentication authentication) {
        authorizationService.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.menu.write");
        List<MenuManifestEntity> items = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
        Map<String, MenuManifestOverrideEntity> overrides = new HashMap<>();
        overrideRepository.findAllByEnterpriseId(enterpriseId).forEach(item -> overrides.put(item.menuId(), item));
        List<MenuManifestItemView> managed = new ArrayList<>();
        for (MenuManifestEntity item : items) {
            MenuManifestOverrideEntity override = overrides.get(item.menuId());
            Effective effective = MenuManifestRules.effective(item.parentMenuId(), item.sortOrder(), item.capabilityCode(), override == null ? null : override.enabled(), override == null ? null : override.parentMenuId(), override == null ? null : override.sortOrder(), override == null ? null : override.capabilityCode());
            managed.add(new MenuManifestItemView(item.menuId(), effective.parentMenuId(), item.path(), item.titleKey(), item.iconKey(),
                    effective.sortOrder(), effective.capabilityCode(), effective.enabled(),
                    effective.enabled() && allowed(effective.capabilityCode(), authentication, enterpriseId) ? actions(item.menuId(), effective.capabilityCode(), authentication, enterpriseId) : List.of(),
                    override == null ? 1 : override.version()));
        }
        managed.sort((left, right) -> MenuManifestRules.compare(left.sortOrder(), left.menuId(), right.sortOrder(), right.menuId()));
        return new MenuManifestView(items.stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0), List.copyOf(managed));
    }

    @Transactional
    public synchronized MenuManifestItemView override(String enterpriseId, String menuId, MenuManifestOverrideCommand request,
                                                String ifMatch, String key, Authentication authentication) {
        authorizationService.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.menu.write");
        if (key == null || key.isBlank()) throw new MenuManifestValidationException("Idempotency-Key is required");
        if (request == null) throw new MenuManifestValidationException("Menu override is required");
        MenuManifestEntity item = manifestRepository.lockMenu(menuId).orElseThrow(() -> new MenuManifestConflictException("Menu item not found"));
        // A locking/current read avoids an older MySQL repeatable-read snapshot after waiting.
        MenuManifestOverrideEntity current = overrideRepository.findByEnterpriseIdAndMenuId(enterpriseId, menuId).orElse(null);
        MenuManifestRules.validateOverride(request.sortOrder(), request.parentMenuId(), request.capabilityCode(), menuId);
        if (request.parentMenuId() != null && !request.parentMenuId().isBlank()
                && !manifestRepository.existsById(request.parentMenuId())) {
            throw new MenuManifestValidationException("Unknown parent menu");
        }
        Map<String, MenuManifestOverrideEntity> parentOverrides = new HashMap<>();
        overrideRepository.findAllByEnterpriseId(enterpriseId).forEach(value -> parentOverrides.put(value.menuId(), value));
        MenuManifestRules.validateOverrideParent(menuId, request.parentMenuId(), parent -> {
            MenuManifestOverrideEntity override = parentOverrides.get(parent);
            if (override != null && override.parentMenuId() != null) return override.parentMenuId();
            return manifestRepository.findById(parent).map(MenuManifestEntity::parentMenuId).orElse(null);
        });
        String fingerprint = fingerprint(enterpriseId, menuId, request, ifMatch);
        Receipt previous = receipts.get("override:" + enterpriseId + ":" + menuId + ":" + key);
        if (previous != null) {
            if (!previous.fingerprint.equals(fingerprint)) throw new MenuManifestConflictException("Idempotency key reused");
            return (MenuManifestItemView) previous.result;
        }
        long currentVersion = current == null ? 1 : current.version();
        if (ifMatch == null || !ifMatch.equals("\"" + currentVersion + "\"")) throw new MenuManifestPreconditionException("The resource has changed");
        if (current == null) {
            current = new MenuManifestOverrideEntity(enterpriseId, menuId, request);
            overrideRepository.saveAndFlush(current);
        } else {
            int changed = overrideRepository.compareAndSet(current.overrideId(), currentVersion, request.enabled(),
                    request.parentMenuId(), request.sortOrder(), request.capabilityCode(), Instant.now());
            if (changed != 1) throw new MenuManifestPreconditionException("The resource has changed");
            // compareAndSet clears the persistence context; this is only the response snapshot.
            current.update(request, currentVersion + 1);
        }
        Effective effective = MenuManifestRules.effective(item.parentMenuId(), item.sortOrder(), item.capabilityCode(), current.enabled(), current.parentMenuId(), current.sortOrder(), current.capabilityCode());
        MenuManifestItemView result = new MenuManifestItemView(item.menuId(), effective.parentMenuId(), item.path(), item.titleKey(), item.iconKey(),
                effective.sortOrder(), effective.capabilityCode(), effective.enabled(),
                effective.enabled() ? actions(item.menuId(), effective.capabilityCode(), authentication, enterpriseId) : List.of(), current.version());
        rememberAfterCommit("override:" + enterpriseId + ":" + menuId + ":" + key, new Receipt(fingerprint, result));
        auditStore.mutation(enterpriseId, menuId, "MENU_OVERRIDE_UPDATED", requestId.currentRequestId(), authentication.getName());
        changeEvents.enqueue(enterpriseId, "MENU_MANIFEST", menuId, "MENU_OVERRIDE_UPDATED", current.version(), requestId.currentRequestId());
        return result;
    }

    private void rememberAfterCommit(String key, Receipt receipt) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Menu mutations require a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { receipts.put(key, receipt); }
        });
    }

    private boolean allowed(String capability, Authentication authentication, String enterpriseId) {
        if (capability == null || capability.isBlank()) return true;
        AuthorizationService.Decision decision = authorizationService.decision(authentication, enterpriseId, capability, null);
        if (!decision.allowed()) return false;
        if (!"LEGACY_SCOPE".equals(decision.reasonCode())) return true;
        boolean enterpriseMenuReader = authentication.getAuthorities().stream()
                .anyMatch(authority -> "SCOPE_enterprise.menu.read".equals(authority.getAuthority()));
        return enterpriseMenuReader || authentication.getAuthorities().stream()
                .anyMatch(authority -> ("SCOPE_" + capability).equals(authority.getAuthority()));
    }

    // Safe action names map to the same operation permissions enforced on the business APIs.
    // This projection never grants a role or substitutes for backend authorization.
    private List<String> actions(String menuId, String capability, Authentication authentication, String enterpriseId) {
        if (!customerMenu(menuId)) return action(capability);
        List<String> result = new ArrayList<>();
        for (String[] operation : List.of(
                new String[]{"read", "GET:/api/customers", "customer.read"},
                new String[]{"create", "POST:/api/customers", "customer.write"},
                new String[]{"update", "PATCH:/api/customers/{username}", "customer.write"},
                new String[]{"delete", "DELETE:/api/customers/{username}", "customer.write"})) {
            boolean scope = authentication.getAuthorities().stream().anyMatch(a -> ("SCOPE_" + operation[2]).equals(a.getAuthority()));
            if (scope && authorizationService.decision(authentication, enterpriseId, operation[1], null).allowed()) result.add(operation[0]);
        }
        return List.copyOf(result);
    }

    private static String fingerprint(Object... values) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static MenuManifestView view(long version, List<MenuManifestEntity> entities, Map<String, MenuManifestOverrideEntity> overrides, String enterpriseId) {
        return new MenuManifestView(version, entities.stream().sorted((left, right) -> MenuManifestRules.compare(left.sortOrder(), left.menuId(), right.sortOrder(), right.menuId())).map(item -> new MenuManifestItemView(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode(), true, action(item.capabilityCode()), item.manifestVersion())).toList());
    }
    private static String manifestFingerprint(MenuManifestSyncCommand request) {
        return fingerprint(new MenuManifestSyncCommand(request.manifestVersion(), request.items().stream()
                .sorted(Comparator.comparing(MenuManifestItem::menuId)).toList()));
    }
    private static MenuManifestSyncCommand toRequest(MenuManifestView view) {
        return new MenuManifestSyncCommand(view.manifestVersion(), view.items().stream()
                .map(item -> new MenuManifestItem(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode())).toList());
    }
    private record Receipt(String fingerprint, Object result) {}
}
