package com.abluepoint.summer.sample.authorization.application.data;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.authorization.domain.permission.PermissionErrorCode;

import org.springframework.stereotype.Component;

import java.util.*;

/** Resource mappings are application code, never permission form input. */
@Component
public class DataResourceRegistry {
    public record Resource(
            String key,
            String name,
            String tenantAttribute,
            String tenantColumn,
            String selfAttribute,
            String selfColumn,
            String selfMeaning,
            String departmentAttribute,
            String departmentColumn,
            Set<String> actions,
            String upperBoundAttribute,
            String upperBoundColumn,
            String upperBoundMeaning,
            String readAdapter,
            String writeAdapter) {}

    private final Map<String, Resource> resources =
            Map.of(
                    "dictionaries", configuration("dictionaries", "业务字典"),
                    "system-parameters", configuration("system-parameters", "系统参数"),
                    "applications",
                    new Resource(
                            "applications",
                            "申请单",
                            "tenantId",
                            "tenant_id",
                            "applicantId",
                            "applicant_id",
                            "申请人（代录人不取得本人归属）",
                            "departmentId",
                            "department_id",
                            Set.of("read", "create", "update", "delete", "export"),
                            "businessVisible",
                            "business_visible",
                            "业务安全上限：可读取标记必须有效",
                            "JPA Specification / bound SQL before pagination",
                            "Domain-locked bound SQL"));

    private static Resource configuration(String key, String name) {
        return new Resource(key, name, "tenantId", "tenant_id", "ownerId", "owner_id",
                "持久所有者（创建人不自动取得本人归属）", "organizationId", "organization_id",
                Set.of("read", "create", "update", "delete", "export"), "", "",
                "各查询保留原状态、发布版本、公共读取及覆盖优先级上限",
                "Registered SQL / JPA projection", "Domain-locked business transaction");
    }

    public RegisteredScopeProjection configurationProjection(DataReadService.Scope scope) {
        if (!Set.of("dictionaries", "system-parameters").contains(scope.resource().key()))
            throw SummerException.withMessage(PermissionErrorCode.INVALID, "Configuration projection required");
        return new RegisteredScopeProjection(scope, new RegisteredScopeProjection.Columns(
                scope.resource().key().equals("dictionaries") ? "PLATFORM" : "ENTERPRISE", null, null, "tenant_id", "tenantId", "owner_id", "ownerId",
                "organization_id", "organizationId"));
    }

    public Collection<Resource> list() {
        return resources.values();
    }

    public Collection<Resource> list(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain) {
        return resources.values().stream().filter(resource -> supports(domain.kind(), resource.key())).toList();
    }

    private boolean supports(String domainKind, String resource) {
        return "applications".equals(resource) || ("dictionaries".equals(resource)
                ? "PLATFORM".equals(domainKind) : "ENTERPRISE".equals(domainKind));
    }

    public Resource require(com.abluepoint.summer.security.authorization.ResourceAuthorization.Domain domain,
            String key, String action) {
        Resource resource = require(key, action);
        if (!supports(domain.kind(), key))
            throw SummerException.withMessage(PermissionErrorCode.INVALID, "Data resource belongs to another domain");
        return resource;
    }

    public Resource require(String key, String action) {
        Resource r = resources.get(key);
        if (r == null || !r.actions().contains(action))
            throw SummerException.withMessage(
                    PermissionErrorCode.INVALID, "Unregistered data resource or action");
        return r;
    }
}
