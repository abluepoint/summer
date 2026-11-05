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

    public Collection<Resource> list() {
        return resources.values();
    }

    public Resource require(String key, String action) {
        Resource r = resources.get(key);
        if (r == null || !r.actions().contains(action))
            throw SummerException.withMessage(
                    PermissionErrorCode.INVALID, "Unregistered data resource or action");
        return r;
    }
}
