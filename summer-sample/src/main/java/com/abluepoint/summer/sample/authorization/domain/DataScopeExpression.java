package com.abluepoint.summer.sample.authorization.domain;

import java.util.*;
import java.util.function.Function;

/** One validated expression drives row checks, JPA predicates and bound SQL. No caller SQL is accepted. */
public final class DataScopeExpression {
    public enum Field {
        TENANT, SELF, CREATOR, OWNER, ORGANIZATION, DEPARTMENT, ORGANIZATION_TREE;
        static Field parse(Object value) {
            if (!(value instanceof String text)) throw invalid("scope field is required");
            String name = text.trim().toUpperCase(Locale.ROOT);
            name = switch (name) {
                case "ORGANIZATION_ID", "ORG", "ORG_ID" -> "ORGANIZATION";
                case "CREATED_BY", "CREATOR_ID" -> "CREATOR";
                case "OWNER_ID" -> "OWNER";
                default -> name;
            };
            try { return valueOf(name); } catch (IllegalArgumentException failure) { throw invalid("unsupported scope field"); }
        }
    }

    public record Row(String tenant, String organization, String creator, String owner, String subject) {
        String value(Field field) {
            return switch (field) {
                case TENANT -> tenant;
                case SELF -> subject;
                case CREATOR -> creator;
                case OWNER -> owner;
                case ORGANIZATION, DEPARTMENT, ORGANIZATION_TREE -> organization;
            };
        }
    }
    public record Context(String enterprise, String subject, String organization,
                   Function<List<String>, List<String>> descendants) {}
    public record Compiled(Node allowed, Node denied) {
        public Node expression() { return and(List.of(allowed, new Not(denied))); }
    }

    public sealed interface Node permits Constant, Comparison, Junction, Not {
        boolean matches(Row row);
    }
    public record Constant(boolean value) implements Node {
        public boolean matches(Row row) { return value; }

    }
    public record Comparison(Field field, boolean negative, List<String> values) implements Node {
        public Comparison { values = List.copyOf(values); }
        public boolean matches(Row row) {
            if (values.isEmpty()) return false;
            String actual = row.value(field);
            return actual != null && (negative != values.contains(actual));
        }

    }
    public record Junction(boolean conjunction, List<Node> children) implements Node {
        public Junction { children = List.copyOf(children); }
        public boolean matches(Row row) {
            return conjunction ? children.stream().allMatch(n -> n.matches(row)) : children.stream().anyMatch(n -> n.matches(row));
        }

    }
    public record Not(Node child) implements Node {
        public boolean matches(Row row) { return !child.matches(row); }

    }
    private static final Node FALSE = new Constant(false);
    private static final Set<String> KEYS = Set.of("field", "type", "operator", "value", "effect", "children", "rules");
    private final Context context;
    private int nodeCount;
    private DataScopeExpression(Context context) { this.context = context; }

    public static Compiled compile(String operator, Object rules, Object conditions, Context context) {
        DataScopeExpression compiler = new DataScopeExpression(context);
        List<Object> roots = new ArrayList<>();
        if (rules instanceof Collection<?> collection) roots.addAll(collection);
        else if (rules != null) throw invalid("rules must be an array");
        if (conditions != null) roots.add(conditions);
        if (roots.isEmpty()) throw invalid("a scope policy requires a condition");
        List<Node> allow = new ArrayList<>();
        List<Node> deny = new ArrayList<>();
        for (Object root : roots) {
            Parsed parsed = compiler.parse(root, 0);
            if (parsed.allow != null) allow.add(parsed.allow);
            deny.addAll(parsed.deny);
        }
        Node allowed = allow.isEmpty() ? FALSE : group(logical(operator), allow);
        // Tenant is an unconditional boundary, including under OR and NOT conditions.
        allowed = and(List.of(new Comparison(Field.TENANT, false, List.of(context.enterprise)), allowed));
        return new Compiled(allowed, deny.isEmpty() ? FALSE : group(false, deny));
    }

    private record Parsed(Node allow, List<Node> deny, Node match) {}
    private Parsed parse(Object source, int depth) {
        if (depth > 24 || ++nodeCount > 256) throw invalid("scope condition is too complex");
        if (!(source instanceof Map<?, ?> node)) throw invalid("scope condition must be an object");
        for (Object key : node.keySet()) if (!KEYS.contains(String.valueOf(key))) throw invalid("unsupported scope condition property");
        String effect = text(node.get("effect"), "ALLOW");
        if (!Set.of("ALLOW", "DENY").contains(effect)) throw invalid("effect must be ALLOW or DENY");
        Object children = node.get("children") == null ? node.get("rules") : node.get("children");
        if (node.get("children") != null && node.get("rules") != null) throw invalid("choose children or rules, not both");
        Object rawField = node.get("field") == null ? node.get("type") : node.get("field");
        if (node.get("field") != null && node.get("type") != null && Field.parse(node.get("field")) != Field.parse(node.get("type"))) {
            throw invalid("field and type must identify the same scope field");
        }
        Node match;
        Node allow;
        List<Node> deny = new ArrayList<>();
        if (rawField == null) {
            if (!(children instanceof Collection<?> values) || values.isEmpty()) throw invalid("logical condition requires children");
            if (node.get("value") != null) throw invalid("logical condition cannot have a value");
            boolean conjunction = logical(text(node.get("operator"), "AND"));
            List<Parsed> parsedChildren = values.stream().map(child -> parse(child, depth + 1)).toList();
            List<Node> allows = parsedChildren.stream().map(Parsed::allow).filter(Objects::nonNull).toList();
            match = group(conjunction, parsedChildren.stream().map(Parsed::match).toList());
            allow = allows.isEmpty() ? null : group(conjunction, allows);
            for (int index = 0; index < parsedChildren.size(); index++) {
                Parsed child = parsedChildren.get(index);
                for (Node denial : child.deny) {
                    List<Node> gates = new ArrayList<>();
                    gates.add(denial);
                    // A denial inside an AND branch keeps that branch's other positive conditions.
                    // OR never permits an allow branch to override a matching denial.
                    if (conjunction) for (int other = 0; other < parsedChildren.size(); other++) {
                        if (other != index && parsedChildren.get(other).allow != null) gates.add(parsedChildren.get(other).allow);
                    }
                    deny.add(and(gates));
                }
            }
        } else {
            if (children instanceof Collection<?> values && !values.isEmpty()) throw invalid("leaf condition cannot have children");
            if (children != null && !(children instanceof Collection<?>)) throw invalid("children must be an array");
            Field field = Field.parse(rawField);
            String comparator = text(node.get("operator"), "EQ");
            comparator = switch (comparator) {
                case "=", "EQUALS" -> "EQ";
                case "!=", "NOT_EQUALS" -> "NE";
                // CONTAINS is set membership (the existing public condition model uses an array value).
                case "CONTAINS" -> "IN";
                default -> comparator;
            };
            if (!Set.of("EQ", "NE", "IN", "NOT_IN").contains(comparator)) throw invalid("unsupported scope comparator");
            Object rawValue = node.get("value");
            List<String> values;
            if (rawValue == null) {
                String value = switch (field) {
                    case TENANT -> context.enterprise;
                    case SELF, CREATOR, OWNER -> context.subject;
                    case ORGANIZATION, DEPARTMENT, ORGANIZATION_TREE -> context.organization;
                };
                values = value == null ? List.of() : List.of(value);
            } else {
                if (("IN".equals(comparator) || "NOT_IN".equals(comparator)) && !(rawValue instanceof Collection<?>)) throw invalid("set comparator requires an array value");
                if (("EQ".equals(comparator) || "NE".equals(comparator)) && rawValue instanceof Collection<?>) throw invalid("equality comparator requires one value");
                Collection<?> supplied = rawValue instanceof Collection<?> c ? c : List.of(rawValue);
                if (supplied.isEmpty() || supplied.size() > 100) throw invalid("scope values must contain 1 to 100 identifiers");
                values = supplied.stream().map(value -> {
                    if (!(value instanceof String s) || s.isBlank() || s.length() > 160) throw invalid("scope value must be a nonblank identifier");
                    return s;
                }).distinct().toList();
            }
            if (field == Field.ORGANIZATION_TREE) values = context.descendants.apply(values);
            match = new Comparison(field, Set.of("NE", "NOT_IN").contains(comparator), values);
            allow = match;
        }
        if ("DENY".equals(effect)) { deny.clear(); deny.add(match); allow = null; }
        return new Parsed(allow, List.copyOf(deny), match);
    }

    private static Node and(List<Node> nodes) { return group(true, nodes); }
    private static Node group(boolean conjunction, List<Node> children) { return children.size() == 1 ? children.getFirst() : new Junction(conjunction, children); }
    private static boolean logical(String operator) {
        return switch (text(operator, "AND")) { case "AND" -> true; case "OR" -> false; default -> throw invalid("operator must be AND or OR"); };
    }
    private static String text(Object value, String fallback) {
        if (value == null) return fallback;
        if (!(value instanceof String s) || s.isBlank()) throw invalid("scope operator must be a string");
        return s.trim().toUpperCase(Locale.ROOT);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
