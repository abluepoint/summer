package com.abluepoint.summer.data.jpa.template;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlResultProjectionAdapterTest {

    private final SqlResultProjectionAdapter adapter = new SqlResultProjectionAdapter();

    @Test
    @SuppressWarnings("unchecked")
    void projectsSupportedSqlResultShapesWithExplicitCamelCaseAliases() {
        Tuple tuple = tuple(List.of(
                Map.entry("customer_id", 7L),
                Map.entry("displayName", "Summer")
        ));

        assertThat(adapter.project(tuple, Map.class))
                .containsEntry("customerId", 7L)
                .containsEntry("displayName", "Summer");
        assertThat(adapter.project(tuple, CustomerRecord.class))
                .isEqualTo(new CustomerRecord(7L, "Summer"));
        assertThat(adapter.project(tuple, CustomerDto.class))
                .extracting(CustomerDto::customerId, CustomerDto::displayName)
                .containsExactly(7L, "Summer");
        assertThat(adapter.project(tuple, CustomerProjection.class).getCustomerId()).isEqualTo(7L);
        assertThat(adapter.project(tuple, Tuple.class)).isSameAs(tuple);
    }

    @Test
    void projectsSingleColumnScalars() {
        Tuple tuple = tuple(List.of(Map.entry("total_count", 7L)));

        assertThat(adapter.project(tuple, Long.class)).isEqualTo(7L);
        assertThat(adapter.project(tuple, String.class)).isEqualTo("7");
    }

    @Test
    @SuppressWarnings("unchecked")
    void preservesNullableColumnsInNamedProjections() {
        Tuple tuple = tuple(List.of(
                Map.entry("customer_id", 7L),
                new AbstractMap.SimpleImmutableEntry<>("display_name", null)
        ));

        assertThat(adapter.project(tuple, Map.class)).containsEntry("displayName", null);
        assertThat(adapter.project(tuple, CustomerRecord.class)).isEqualTo(new CustomerRecord(7L, null));
    }

    @Test
    void rejectsSetterBeansAndOrderedLists() {
        Tuple tuple = tuple(List.of(Map.entry("display_name", "Summer")));

        assertThatThrownBy(() -> adapter.project(tuple, CustomerBean.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported SQL result projection type");
        assertThatThrownBy(() -> adapter.project(tuple, List.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported SQL result projection type");
    }

    @Test
    void rejectsAliasesThatCollapseToTheSameCamelCaseName() {
        Tuple tuple = tuple(List.of(
                Map.entry("customer_id", 7L),
                Map.entry("customerId", 8L)
        ));

        assertThatThrownBy(() -> adapter.project(tuple, Map.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate SQL result alias")
                .hasMessageContaining("customerId");
    }

    private static Tuple tuple(List<Map.Entry<String, Object>> entries) {
        Tuple tuple = mock(Tuple.class);
        List<TupleElement<?>> elements = new ArrayList<>();
        entries.forEach(entry -> {
            TupleElement<?> element = mock(TupleElement.class);
            when(element.getAlias()).thenReturn(entry.getKey());
            when(tuple.get(entry.getKey())).thenReturn(entry.getValue());
            elements.add(element);
        });
        when(tuple.getElements()).thenReturn(elements);
        for (int index = 0; index < entries.size(); index++) {
            when(tuple.get(index)).thenReturn(entries.get(index).getValue());
        }
        return tuple;
    }

    record CustomerRecord(Long customerId, String displayName) {
    }

    static final class CustomerDto {
        private final Long customerId;
        private final String displayName;

        CustomerDto(Long customerId, String displayName) {
            this.customerId = customerId;
            this.displayName = displayName;
        }

        Long customerId() {
            return customerId;
        }

        String displayName() {
            return displayName;
        }
    }

    interface CustomerProjection {
        Long getCustomerId();
    }

    static final class CustomerBean {
        private String displayName;

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
