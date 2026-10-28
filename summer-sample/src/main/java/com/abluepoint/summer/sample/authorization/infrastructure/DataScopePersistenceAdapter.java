package com.abluepoint.summer.sample.authorization.infrastructure;

import com.abluepoint.summer.sample.authorization.domain.DataScopeExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;

/** Renders the validated business expression without accepting caller SQL. */
public final class DataScopePersistenceAdapter {
    private DataScopePersistenceAdapter() {}

    public static <T> Specification<T> specification(DataScopeExpression.Node node) {
        return (root, query, builder) -> predicate(node, root, builder);
    }

    private static Predicate predicate(DataScopeExpression.Node node, Root<?> root, CriteriaBuilder builder) {
        return switch (node) {
            case DataScopeExpression.Constant value -> value.value() ? builder.conjunction() : builder.disjunction();
            case DataScopeExpression.Comparison value -> {
                if (value.values().isEmpty()) yield builder.disjunction();
                Predicate match = root.get(attribute(value.field())).in(value.values());
                yield builder.and(builder.isNotNull(root.get(attribute(value.field()))), value.negative() ? builder.not(match) : match);
            }
            case DataScopeExpression.Junction value -> {
                Predicate[] children = value.children().stream().map(child -> predicate(child, root, builder)).toArray(Predicate[]::new);
                yield value.conjunction() ? builder.and(children) : builder.or(children);
            }
            case DataScopeExpression.Not value -> builder.not(predicate(value.child(), root, builder));
        };
    }
    private static String attribute(DataScopeExpression.Field field) {
        return switch (field) {
            case TENANT -> "tenantId";
            case SELF, CREATOR -> "createdBy";
            case OWNER -> "ownerId";
            case ORGANIZATION, DEPARTMENT, ORGANIZATION_TREE -> "organizationId";
        };
    }
    private static String column(DataScopeExpression.Field field) {
        return switch (field) {
            case TENANT -> "tenant_id";
            case SELF, CREATOR -> "created_by";
            case OWNER -> "owner_id";
            case ORGANIZATION, DEPARTMENT, ORGANIZATION_TREE -> "organization_id";
        };
    }
    public record Sql(String predicate, Map<String, Object> bindings) {}
    public static Sql sql(DataScopeExpression.Node node) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        return new Sql(sql(node, bindings), Collections.unmodifiableMap(bindings));
    }
    private static String sql(DataScopeExpression.Node node, Map<String, Object> bindings) {
        return switch (node) {
            case DataScopeExpression.Constant value -> value.value() ? "1 = 1" : "1 = 0";
            case DataScopeExpression.Comparison value -> {
                if (value.values().isEmpty()) yield "1 = 0";
                List<String> parameters = new ArrayList<>();
                for (String item : value.values()) {
                    String name = "dataScope" + bindings.size();
                    bindings.put(name, item);
                    parameters.add(":" + name);
                }
                String column = column(value.field());
                yield "(" + column + " is not null and " + column + (value.negative() ? " not in (" : " in (")
                        + String.join(",", parameters) + "))";
            }
            case DataScopeExpression.Junction value -> {
                if (value.children().isEmpty()) yield value.conjunction() ? "1 = 1" : "1 = 0";
                yield "(" + String.join(value.conjunction() ? " and " : " or ", value.children().stream().map(child -> sql(child, bindings)).toList()) + ")";
            }
            case DataScopeExpression.Not value -> "not (" + sql(value.child(), bindings) + ")";
        };
    }
}
