package com.abluepoint.summer.sample.resource;

import com.abluepoint.summer.sample.resource.domain.InterfaceResourceCatalog;
import com.abluepoint.summer.sample.resource.domain.InterfaceResourceDescriptor;
import com.abluepoint.summer.sample.resource.domain.ResourceType;
import com.abluepoint.summer.sample.resource.domain.SensitiveLevel;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class InterfaceResourceCatalogTest {
    private InterfaceResourceDescriptor row(String path, String handler, String context, Map<String,List<String>> conditions) {
        return new InterfaceResourceDescriptor("get", path, handler, "example", ResourceType.BUSINESS, SensitiveLevel.NORMAL,
                null, true, context, "MVC", conditions, "MANAGED_ROLE");
    }
    @Test void identicalNormalizedMappingsAreDeduplicated() {
        var catalog = new InterfaceResourceCatalog(List.of(row("//api//same/", "A#read", "application", Map.of()), row("/api/same", "A#read", "application", Map.of())));
        assertThat(catalog.items()).hasSize(1);
        assertThat(catalog.items().getFirst().permissionCode()).isEqualTo("GET:/api/same");
    }
    @Test void variableNameAliasesCannotHideAnAmbiguousRoute() {
        assertThatThrownBy(() -> new InterfaceResourceCatalog(List.of(row("/api/items/{id}", "A#read", "application", Map.of()), row("/api/items/{name}", "B#read", "application", Map.of()))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting interface resource");
    }
    @Test void identicalConditionsConflictButDisjointConditionsAndDifferentContextsRemainVisible() {
        assertThatThrownBy(() -> new InterfaceResourceCatalog(List.of(row("/api/item", "A#read", "application", Map.of("params", List.of("mode=a"))), row("/api/item", "B#read", "application", Map.of("params", List.of("mode=a"))))))
                .isInstanceOf(IllegalStateException.class);
        var catalog = new InterfaceResourceCatalog(List.of(row("/api/item", "A#read", "application", Map.of("params", List.of("mode=a"))), row("/api/item", "B#read", "application", Map.of("params", List.of("mode=b"))), row("/api/item", "C#read", "management", Map.of("params", List.of("mode=a")))));
        assertThat(catalog.items()).hasSize(3);
    }    @Test void overlappingEqualSpecificityConditionsAreRejected() {
        assertThatThrownBy(() -> new InterfaceResourceCatalog(List.of(
                row("/api/item", "A#read", "application", Map.of("params", List.of("left"))),
                row("/api/item", "B#read", "application", Map.of("params", List.of("right"))))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Conflicting interface resource");
    }

}
