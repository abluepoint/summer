package com.abluepoint.summer.sample.menu;

import com.abluepoint.summer.sample.authorization.domain.permission.ManagementConsumerMenus;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.error.SampleTransportErrorCode;

import com.abluepoint.summer.sample.authorization.AuthorizationService;
import com.abluepoint.summer.sample.authorization.AuthorizationChangeEventService;
import com.abluepoint.summer.sample.authorization.AuthorizationAuditStore;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.abluepoint.summer.sample.dictionary.PlatformDictionaryOptions;
import java.util.concurrent.ConcurrentHashMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
class MenuManifestService {
    private final MenuManifestRepository manifestRepository;
    private final AuthorizationService authorizationService;
    private final AuthorizationChangeEventService changeEvents;
    private final RequestIdAccessor requestId;
    private final AuthorizationAuditStore auditStore;
    private final com.abluepoint.summer.sample.authorization.MenuPublication publication;
    private final PlatformDictionaryOptions dictionaries;
    private final Map<String, Receipt> receipts = new ConcurrentHashMap<>();

    MenuManifestService(MenuManifestRepository manifestRepository,
                        AuthorizationService authorizationService, AuthorizationChangeEventService changeEvents,
                        RequestIdAccessor requestId, AuthorizationAuditStore auditStore,
                        com.abluepoint.summer.sample.authorization.MenuPublication publication, PlatformDictionaryOptions dictionaries) {
        this.dictionaries = dictionaries;
        this.publication = publication;
        this.manifestRepository = manifestRepository;
        this.authorizationService = authorizationService;
        this.changeEvents = changeEvents;
        this.requestId = requestId;
        this.auditStore = auditStore;
    }

    private List<MenuManifestEntity> currentItems() {
        long version = publication.read().manifestVersion();
        return manifestRepository.findAllByOrderBySortOrderAscMenuIdAsc().stream()
                .filter(item -> item.manifestVersion() == version).toList();
    }

    @Transactional(readOnly = true)
    synchronized MenuManifestView current(Authentication authentication) {
        authorizationService.requirePlatformMenu(authentication);
        List<MenuManifestEntity> items = currentItems();
        long version = publication.read().manifestVersion();
        return new MenuManifestView(version, view(version, items).items(), dictionaries.published(PlatformDictionaryOptions.MENU_CLIENT));
    }

    @Transactional
    synchronized MenuManifestView sync(MenuManifestSyncRequest request, String key, Authentication authentication) {
        authorizationService.requirePlatformMenu(authentication);
        if (key == null || key.isBlank()) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Idempotency-Key is required");
        publication.lock(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform());
        validate(request);
        String fingerprint = manifestFingerprint(request);
        Receipt previous = receipts.get(key);
        if (previous != null) {
            if (!previous.fingerprint.equals(fingerprint)) throw SummerException.withMessage(MenuManifestErrorCode.CONFLICT, "Idempotency key reused");
            return (MenuManifestView) previous.result;
        }
        long current = publication.read().manifestVersion();
        if (request.manifestVersion() < current) throw SummerException.withMessage(MenuManifestErrorCode.CONFLICT, "Manifest version must increase");
        var clients = dictionaries.published(PlatformDictionaryOptions.MENU_CLIENT);
        Map<String,String> previousClients = new HashMap<>();
        currentItems().forEach(item -> previousClients.put(item.menuId(), item.clientCode()));
        var normalized = request.items().stream().map(item -> {
            String previousClient = previousClients.get(item.menuId());
            String client = item.clientCode() == null ? Objects.requireNonNullElse(previousClient, PlatformDictionaryOptions.WEB_CLIENT) : item.clientCode();
            if (!client.equals(previousClient) && clients.stream().noneMatch(option -> option.enabled() && option.value().equals(client)))
                throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "请选择已发布且启用的所属客户端");
            return new MenuManifestItemRequest(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode(), client);
        }).toList();
        if (request.manifestVersion() == current) {
            List<MenuManifestEntity> existing = currentItems();
            MenuManifestView replay = view(current, existing);
            if (manifestFingerprint(new MenuManifestSyncRequest(current, normalized)).equals(manifestFingerprint(toRequest(replay)))) return replay;
            throw SummerException.withMessage(MenuManifestErrorCode.CONFLICT, "Manifest version already contains different metadata");
        }
        manifestRepository.deleteAll();
        List<MenuManifestEntity> entities = normalized.stream().map(item -> new MenuManifestEntity(item, request.manifestVersion())).toList();
        manifestRepository.saveAll(entities);
        publication.published(request.manifestVersion());
        MenuManifestView result = view(request.manifestVersion(), entities);
        rememberAfterCommit(key, new Receipt(fingerprint, result));
        auditStore.mutation("PLATFORM:platform", "manifest", "MENU_MANIFEST_SYNCED", requestId.currentRequestId(), authentication.getName());
        changeEvents.enqueue(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain.platform(), "MENU_MANIFEST", "manifest", "MENU_MANIFEST_SYNCED", request.manifestVersion(), requestId.currentRequestId());
        return result;
    }

    synchronized MenuManifestView visible(String enterpriseId, Authentication authentication) {
        authorizationService.requireEnterpriseMembership(enterpriseId, authentication);
        List<MenuManifestEntity> items = currentItems();
        List<MenuManifestItemView> visible = new ArrayList<>();
        long version = publication.read().manifestVersion();
        for (MenuManifestEntity item : items) {
            if (ManagementConsumerMenus.PLATFORM_DICTIONARY_PAGES.contains(item.menuId())) continue;
            if (!allowed(item.capabilityCode(), authentication, enterpriseId)) continue;
            List<String> actions = actions(item.menuId(), item.capabilityCode(), authentication, enterpriseId);
            if (customerMenu(item.menuId()) && !actions.contains("read")) continue;
            visible.add(new MenuManifestItemView(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(),
                    item.iconKey(), item.sortOrder(), item.capabilityCode(), true, actions,
                    1, item.clientCode()));
        }
        Map<String, String> parents = new HashMap<>();
        Set<String> visibleIds = new HashSet<>();
        visible.forEach(item -> { visibleIds.add(item.menuId()); parents.put(item.menuId(), item.parentMenuId()); });
        visible.removeIf(item -> !visibleAncestors(item, parents, visibleIds));
        visible.sort(Comparator.comparingInt(MenuManifestItemView::sortOrder).thenComparing(MenuManifestItemView::menuId));
        return new MenuManifestView(version, List.copyOf(visible));
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
    static void validate(MenuManifestSyncRequest request) {
        if (request == null || request.manifestVersion() < 1 || request.items() == null) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "manifestVersion and items are required");
        Set<String> ids = new HashSet<>();
        for (MenuManifestItemRequest item : request.items()) {
            if (item == null || blank(item.menuId()) || !item.menuId().matches("[A-Za-z0-9._:-]+(?:/[A-Za-z0-9._:-]+)*") || item.menuId().length() > 160) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Invalid menuId");
            if (!ids.add(item.menuId())) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Duplicate menuId");
            if (blank(item.path()) || !item.path().startsWith("/") || item.path().contains("://") || item.path().contains("${") || item.path().contains("?")) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Invalid menu path");
            if (blank(item.titleKey()) || blank(item.iconKey()) || item.sortOrder() == null || item.sortOrder() < 0) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Invalid menu metadata");
            if (item.capabilityCode() != null && (item.capabilityCode().isBlank() || item.capabilityCode().length() > 200)) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Invalid capability code");
            if (item.parentMenuId() != null && item.parentMenuId().equals(item.menuId())) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Menu cannot parent itself");
        }
        for (MenuManifestItemRequest item : request.items()) {
            if (item.parentMenuId() != null && !ids.contains(item.parentMenuId())) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Unknown parent menu");
            String parent = item.parentMenuId(); Set<String> visited = new HashSet<>();
            while (parent != null) {
                if (!visited.add(parent)) throw SummerException.withMessage(MenuManifestErrorCode.INVALID, "Menu parent cycle");
                String currentParent = parent;
                MenuManifestItemRequest parentItem = request.items().stream().filter(x -> currentParent.equals(x.menuId())).findFirst().orElse(null);
                parent = parentItem == null ? null : parentItem.parentMenuId();
            }
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String fingerprint(Object... values) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static MenuManifestView view(long version, List<MenuManifestEntity> entities) {
        return new MenuManifestView(version, entities.stream().sorted(Comparator.comparingInt(MenuManifestEntity::sortOrder).thenComparing(MenuManifestEntity::menuId)).map(item -> new MenuManifestItemView(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode(), true, action(item.capabilityCode()), item.manifestVersion(), item.clientCode())).toList());
    }
    private static String manifestFingerprint(MenuManifestSyncRequest request) {
        return fingerprint(new MenuManifestSyncRequest(request.manifestVersion(), request.items().stream()
                .sorted(Comparator.comparing(MenuManifestItemRequest::menuId)).toList()));
    }
    private static MenuManifestSyncRequest toRequest(MenuManifestView view) {
        return new MenuManifestSyncRequest(view.manifestVersion(), view.items().stream()
                .map(item -> new MenuManifestItemRequest(item.menuId(), item.parentMenuId(), item.path(), item.titleKey(), item.iconKey(), item.sortOrder(), item.capabilityCode(), item.clientCode())).toList());
    }
    private record Receipt(String fingerprint, Object result) {}
}
