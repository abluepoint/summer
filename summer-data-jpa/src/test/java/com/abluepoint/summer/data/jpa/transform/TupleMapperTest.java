package com.abluepoint.summer.data.jpa.transform;

import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TupleMapperTest {

    private final TupleMapper mapper = new TupleMapper();

    @Test
    void mapsTupleToMapRecordBeanAndInterface() {
        Tuple tuple = tuple(Map.of("customer_id", 7L, "display_name", "Summer"));

        assertThat(mapper.toMap(tuple)).containsEntry("customerId", 7L).containsEntry("displayName", "Summer");
        assertThat(mapper.map(tuple, CustomerRecord.class)).isEqualTo(new CustomerRecord(7L, "Summer"));
        assertThat(mapper.map(tuple, CustomerBean.class).getDisplayName()).isEqualTo("Summer");
        assertThat(mapper.map(tuple, CustomerView.class).getCustomerId()).isEqualTo(7L);
    }

    @Test
    void preservesExistingCamelCaseAliases() {
        Tuple tuple = tuple(Map.of("customerId", 7L, "displayName", "Summer"));

        assertThat(mapper.map(tuple, CustomerRecord.class)).isEqualTo(new CustomerRecord(7L, "Summer"));
    }

    @Test
    void mapsNullableColumnsToList() {
        Tuple tuple = tupleWithEntries(List.of(
                Map.entry("customer_id", 7L),
                new java.util.AbstractMap.SimpleImmutableEntry<>("display_name", null)
        ));

        assertThat(mapper.toList(tuple)).containsExactly(7L, null);
    }

    private static Tuple tuple(Map<String, Object> values) {
        return tupleWithEntries(values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList());
    }

    private static Tuple tupleWithEntries(List<Map.Entry<String, Object>> entries) {
        Tuple tuple = mock(Tuple.class);
        List<TupleElement<?>> elements = new java.util.ArrayList<>();
        entries.forEach(entry -> {
            TupleElement<?> element = mock(TupleElement.class);
            when(element.getAlias()).thenReturn(entry.getKey());
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

    interface CustomerView {
        Long getCustomerId();
    }

    public static class CustomerBean {
        private String displayName;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }
}
