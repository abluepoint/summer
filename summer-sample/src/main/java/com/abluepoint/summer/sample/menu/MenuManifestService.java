package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
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

@Service
class MenuManifestService {
    private final MenuManifestRepository manifestRepository;
    private final MenuManifestOverrideRepository overrideRepository;
    private final AuthorizationService authorizationService;
    private final AuthorizationChangeEventService changeEvents;
    private final RequestIdAccessor requestId;
    private final AuthorizationAuditStore auditStore;
    private final Map<String, Receipt> receipts = new ConcurrentHashMap<>();

    MenuManifestService(MenuManifestRepository manifestRepository, MenuManifestOverrideRepository overrideRepository,
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
    synchronized MenuManifestView current(Authentication authentication) {
        authorizationService.requirePlatformMenu(authentication);
        List<MenuManifestEntity> items = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
        long version = items.stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0);
        return view(version, items, Map.of(), null);
    }

    @Transactional
    synchronized MenuManifestView sync(MenuManifestSyncRequest request, String key, Authentication authentication) {
        authorizationService.requirePlatformMenu(authentication);
        if (key == null || key.isBlank()) throw new MenuManifestValidationException("Idempotency-Key is required");
        validate(request);
        String fingerprint = manifestFingerprint(request);
        Receipt previous = receipts.get(key);
        if (previous != null) {
            if (!previous.fingerprint.equals(fingerprint)) throw new MenuManifestConflictException("Idempotency key reused");
            return (MenuManifestView) previous.result;
        }
        long current = manifestRepository.findAll().stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0);
        if (request.manifestVersion() < current) throw new MenuManifestConflictException("Manifest version must increase");
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

    synchronized MenuManifestView visible(String enterpriseId, Authentication authentication) {
        authorizationService.requireEnterpriseMembership(enterpriseId, authentication);
        List<MenuManifestEntity> items = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
        Map<String, MenuManifestOverrideEntity> overrides = new HashMap<>();
        overrideRepository.findAllByEnterpriseId(enterpriseId).forEach(item -> overrides.put(item.menuId(), item));
        List<MenuManifestItemView> visible = new ArrayList<>();
        long version = items.stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0);
        for (MenuManifestEntity item : items) {
            MenuManifestOverrideEntity override = overrides.get(item.menuId());
            Effective effective = effective(item, override);
            if (!effective.enabled || !allowed(effective.capabilityCode, authentication, enterpriseId)) continue;
            List<String> actions = actions(item.menuId(), effective.capabilityCode, authentication, enterpriseId);
            if (customerMenu(item.menuId()) && !actions.contains("read")) continue;
            visible.add(new MenuManifestItemView(item.menuId(), effective.parentMenuId, item.path(), item.titleKey(),
                    item.iconKey(), effective.sortOrder, effective.capabilityCode, true, actions,
                    override == null ? 1 : override.version()));
        }
        Map<String, String> parents = new HashMap<>();
        Set<String> visibleIds = new HashSet<>();
        visible.forEach(item -> { visibleIds.add(item.menuId()); parents.put(item.menuId(), item.parentMenuId()); });
        visible.removeIf(item -> !visibleAncestors(item, parents, visibleIds));
        visible.sort(Comparator.comparingInt(MenuManifestItemView::sortOrder).thenComparing(MenuManifestItemView::menuId));
        return new MenuManifestView(version, List.copyOf(visible));
    }

    synchronized MenuManifestView management(String enterpriseId, Authentication authentication) {
        authorizationService.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.menu.write");
        List<MenuManifestEntity> items = manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc();
        Map<String, MenuManifestOverrideEntity> overrides = new HashMap<>();
        overrideRepository.findAllByEnterpriseId(enterpriseId).forEach(item -> overrides.put(item.menuId(), item));
        List<MenuManifestItemView> managed = new ArrayList<>();
        for (MenuManifestEntity item : items) {
            MenuManifestOverrideEntity override = overrides.get(item.menuId());
            Effective effective = effective(item, override);
            managed.add(new MenuManifestItemView(item.menuId(), effective.parentMenuId, item.path(), item.titleKey(), item.iconKey(),
                    effective.sortOrder, effective.capabilityCode, effective.enabled,
                    effective.enabled && allowed(effective.capabilityCode, authentication, enterpriseId) ? actions(item.menuId(), effective.capabilityCode, authentication, enterpriseId) : List.of(),
                    override == null ? 1 : override.version()));
        }
        managed.sort(Comparator.comparingInt(MenuManifestItemView::sortOrder).thenComparing(MenuManifestItemView::menuId));
        return new MenuManifestView(items.stream().mapToLong(MenuManifestEntity::manifestVersion).max().orElse(0), List.copyOf(managed));
    }

    @Transactional
    synchronized MenuManifestItemView override(String enterpriseId, String menuId, MenuManifestOverrideRequest request,
                                                String ifMatch, String key, Authentication authentication) {
        authorizationService.requireEnterpriseMenu(enterpriseId, authentication, "enterprise.menu.write");
        if (key == null || key.isBlank()) throw new MenuManifestValidationException("Idempotency-Key is required");
        if (request == null) throw new MenuManifestValidationException("Menu override is required");
        MenuManifestEntity item = manifestRepository.lockMenu(menuId).orElseThrow(() -> new MenuManifestConflictException("Menu item not found"));
        // A locking/current read avoids an older MySQL repeatable-read snapshot after waiting.
        MenuManifestOverrideEntity current = overrideRepository.findByEnterpriseIdAndMenuId(enterpriseId, menuId).orElse(null);
        validateOverride(request, menuId);
        if (request.parentMenuId() != null && !request.parentMenuId().isBlank()
                && !manifestRepository.existsById(request.parentMenuId())) {
            throw new MenuManifestValidationException("Unknown parent menu");
        }
        validateOverrideParent(enterpriseId, menuId, request.parentMenuId());
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
        Effective effective = effective(item, current);
        MenuManifestItemView result = new MenuManifestItemView(item.menuId(), effective.parentMenuId, item.path(), item.titleKey(), item.iconKey(),
                effective.sortOrder, effective.capabilityCode, effective.enabled,
                effective.enabled ? actions(item.menuId(), effective.capabilityCode, authentication, enterpriseId) : List.of(), current.version());
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

    private static boolean visibleAncestors(MenuManifestItemView item, Map<String, String> parents, Set<String> visibleIds) {
        Set<String> visited = new HashSet<>();
        String parent = item.parentMenuId();
        while (parent != null) {
            if (!visibleIds.contains(parent) || !visited.add(parent)) return false;
            parent = parents.get(parent);
        }
        return true;
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
    private static boolean customerMenu(String menuId) {
        return Set.of("customer", "customer/drawer", "customer/modal", "customer/page").contains(menuId);
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

    private static List<String> action(String capability) {
        if (capability == null || capability.isBlank()) return List.of();
        int dot = capability.lastIndexOf('.');
        return dot < 0 || dot == capability.length() - 1 ? List.of("read") : List.of(capability.substring(dot + 1));
    }
    private static Effective effective(MenuManifestEntity item, MenuManifestOverrideEntity override) {
        return new Effective(override == null || override.enabled() == null || override.enabled(),
                override == null || override.parentMenuId() == null ? item.parentMenuId() : blankToNull(override.parentMenuId()),
                override == null || override.sortOrder() == null ? item.sortOrder() : override.sortOrder(),
                override == null || override.capabilityCode() == null ? item.capabilityCode() : blankToNull(override.capabilityCode()));
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }

    private static void validate(MenuManifestSyncRequest request) {
        if (request == null || request.manifestVersion() < 1 || request.items() == null) throw new MenuManifestValidationException("manifestVersion and items are required");
        Set<String> ids = new HashSet<>();
        for (MenuManifestItemRequest item : request.items()) {
            if (item == null || blank(item.menuId()) || !item.menuId().matches("[A-Za-z0-9._:-]+(?:/[A-Za-z0-9._:-]+)*") || item.menuId().length() > 160) throw new MenuManifestValidationException("Invalid menuId");
            if (!ids.add(item.menuId())) throw new MenuManifestValidationException("Duplicate menuId");
            if (blank(item.path()) || !item.path().startsWith("/") || item.path().contains("://") || item.path().contains("${") || item.path().contains("?")) throw new MenuManifestValidationException("Invalid menu path");
            if (blank(item.titleKey()) || blank(item.iconKey()) || item.sortOrder() == null || item.sortOrder() < 0) throw new MenuManifestValidationException("Invalid menu metadata");
            if (item.capabilityCode() != null && (item.capabilityCode().isBlank() || item.capabilityCode().length() > 200)) throw new MenuManifestValidationException("Invalid capability code");
            if (item.parentMenuId() != null && item.parentMenuId().equals(item.menuId())) throw new MenuManifestValidationException("Menu cannot parent itself");
        }
        for (MenuManifestItemRequest item : request.items()) {
            if (item.parentMenuId() != null && !ids.contains(item.parentMenuId())) throw new MenuManifestValidationException("Unknown parent menu");
            String parent = item.parentMenuId(); Set<String> visited = new HashSet<>();
            while (parent != null) {
                if (!visited.add(parent)) throw new MenuManifestValidationException("Menu parent cycle");
                String currentParent = parent;
                MenuManifestItemRequest parentItem = request.items().stream().filter(x -> currentParent.equals(x.menuId())).findFirst().orElse(null);
                parent = parentItem == null ? null : parentItem.parentMenuId();
            }
        }
    }
    private static void validateOverride(MenuManifestOverrideRequest request, String menuId) {
        if (request.sortOrder() != null && request.sortOrder() < 0) throw new MenuManifestValidationException("Invalid sortOrder");
        if (request.parentMenuId() != null && request.parentMenuId().equals(menuId)) throw new MenuManifestValidationException("Menu cannot parent itself");
        if (request.capabilityCode() != null && (request.capabilityCode().length() > 200 || request.capabilityCode().contains("${"))) throw new MenuManifestValidationException("Invalid capability code");
    }
    private void validateOverrideParent(String enterpriseId, String menuId, String requestedParent) {
        String parent = blankToNull(requestedParent);
        Map<String, MenuManifestOverrideEntity> overrides = new HashMap<>();
        overrideRepository.findAllByEnterpriseId(enterpriseId).forEach(value -> overrides.put(value.menuId(), value));
        Set<String> visited = new HashSet<>();
        while (parent != null) {
            if (!visited.add(parent) || menuId.equals(parent)) throw new MenuManifestValidationException("Menu parent cycle");
            MenuManifestOverrideEntity override = overrides.get(parent);
            if (override != null && override.parentMenuId() != null) parent = blankToNull(override.parentMenuId());
            else parent = manifestRepository.findById(parent).map(MenuManifestEntity::parentMenuId).orElse(null);
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String fingerprint(Object... values) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static MenuManifestView view(long version, List<MenuManifestEntity> entities, Map<String, MenuManifestOverrideEntity> overrides, String enterpriseId) {
        return new MenuManifestView(version, entities.stream().sorted(Comparator.comparingInt(MenuManifestEntity::sortOrder).thenComparing(MenuManifestEntity::menuId)).map(item -> new MenuManifestItemView(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode(), true, action(item.capabilityCode()), item.manifestVersion())).toList());
    }
    private static String manifestFingerprint(MenuManifestSyncRequest request) {
        return fingerprint(new MenuManifestSyncRequest(request.manifestVersion(), request.items().stream()
                .sorted(Comparator.comparing(MenuManifestItemRequest::menuId)).toList()));
    }
    private static MenuManifestSyncRequest toRequest(MenuManifestView view) {
        return new MenuManifestSyncRequest(view.manifestVersion(), view.items().stream()
                .map(item -> new MenuManifestItemRequest(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode())).toList());
    }
    private record Effective(boolean enabled, String parentMenuId, int sortOrder, String capabilityCode) {}
    private record Receipt(String fingerprint, Object result) {}
}
