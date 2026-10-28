package com.abluepoint.summer.sample.resource.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class InterfaceResourceBindingPolicyTest {
    private static final String ORIGINAL = "example.before.CustomerController";
    private static final String CURRENT = "example.after.api.CustomerController";
    private static final String OLD_REQUEST = "example.before.CustomerRequest";
    private static final String NEW_REQUEST = "example.after.api.CustomerRequest";

    @Test void retainsAnObservedBindingAcrossOnlyCataloguedTypeMoves() {
        var policy = new InterfaceResourceBindingPolicy(Map.of(CURRENT, ORIGINAL, NEW_REQUEST, OLD_REQUEST));
        var previous = row(ORIGINAL + "#read[class " + OLD_REQUEST + "]");
        var current = row(CURRENT + "#read[class " + NEW_REQUEST + "]");
        var catalog = policy.protect(new InterfaceResourceCatalog(List.of(current)),
                List.of(new InterfaceResourceCatalogView(List.of(previous), 1)));
        assertThat(catalog.items().getFirst().configurationPolicy()).isEqualTo("MANAGED_ROLE");
        assertThat(policy.isReplaced(current.permissionCode(), current.handler())).isFalse();
    }

    @Test void retainsNestedAndArrayParameterIdentityWithoutRewritingMetadata() {
        var policy = new InterfaceResourceBindingPolicy(Map.of(CURRENT, ORIGINAL, NEW_REQUEST, OLD_REQUEST));
        var previous = row(ORIGINAL + "#read[class [[L" + OLD_REQUEST + ";, class " + OLD_REQUEST + "$Child]");
        var current = row(CURRENT + "#read[class [[L" + NEW_REQUEST + ";, class " + NEW_REQUEST + "$Child]");
        var catalog = policy.protect(new InterfaceResourceCatalog(List.of(current)),
                List.of(new InterfaceResourceCatalogView(List.of(previous), 1)));
        assertThat(catalog.items().getFirst()).isEqualTo(current);
        assertThat(policy.isReplaced(current.permissionCode(), current.handler())).isFalse();
    }

    @Test void aRealReplacementStillRequiresAnExplicitPermission() {
        var policy = new InterfaceResourceBindingPolicy(Map.of(CURRENT, ORIGINAL));
        var original = row(ORIGINAL + "#read[]");
        for (String handler : List.of(CURRENT + "#replacement[]", "example.other.CustomerController#read[]",
                CURRENT + "Extra#read[]")) {
            var replacement = row(handler);
            var catalog = policy.protect(new InterfaceResourceCatalog(List.of(replacement)),
                    List.of(new InterfaceResourceCatalogView(List.of(original), 1)));
            assertThat(catalog.items().getFirst().configurationPolicy()).isEqualTo("REPLACED_REQUIRES_EXPLICIT_PERMISSION");
            assertThat(policy.isReplaced(replacement.permissionCode(), handler)).isTrue();
            policy.remember(catalog);
            assertThat(policy.isReplaced(replacement.permissionCode(), handler)).isTrue();
        }
    }

    private static InterfaceResourceDescriptor row(String handler) {
        return new InterfaceResourceDescriptor("GET", "/api/customers", handler, "customer", ResourceType.BUSINESS,
                SensitiveLevel.NORMAL, "GET:/api/customers", true, "application", "ANNOTATION", Map.of(), "MANAGED_ROLE");
    }
}
