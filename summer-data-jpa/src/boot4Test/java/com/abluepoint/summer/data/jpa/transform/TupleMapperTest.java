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

    private static Tuple tuple(Map<String, Object> values) {
        Tuple tuple = mock(Tuple.class);
        List<String> aliases = values.keySet().stream().sorted().toList();
        List<TupleElement<?>> elements = new java.util.ArrayList<>();
        aliases.forEach(alias -> {
            TupleElement<?> element = mock(TupleElement.class);
            when(element.getAlias()).thenReturn(alias);
            elements.add(element);
        });
        when(tuple.getElements()).thenReturn(elements);
        for (int index = 0; index < aliases.size(); index++) {
            when(tuple.get(index)).thenReturn(values.get(aliases.get(index)));
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
