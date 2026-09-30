package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.sample.shared.CustomerSummaryView;
import com.abluepoint.summer.sample.shared.CustomerView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CustomerPersistenceTest {

    @Autowired
    CustomerRepository repository;

    @Autowired
    CustomerQuery customerQuery;

    @Test
    void initializesEntityIdentityAndAuditFieldsWhenPersisted() {
        Customer saved = repository.saveAndFlush(new Customer("audit@summer.local", "Audit Customer"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void complexReadsUseNarrowSummaryAndDetailProjections() {
        repository.saveAndFlush(new Customer("projection", "projection@summer.local", "Projection Customer"));

        List<?> summary = customerQuery.list(new CustomerListQuery(
                0, 20, null, CustomerListQuery.SortField.USERNAME,
                CustomerListQuery.SortDirection.ASC, CustomerListQuery.Projection.SUMMARY));
        List<?> detail = customerQuery.list(new CustomerListQuery(
                0, 20, null, CustomerListQuery.SortField.USERNAME,
                CustomerListQuery.SortDirection.ASC, CustomerListQuery.Projection.DETAIL));

        assertThat(summary).isNotEmpty().allMatch(CustomerSummaryView.class::isInstance);
        assertThat(detail).isNotEmpty().allMatch(CustomerView.class::isInstance);
        assertThat(List.of(CustomerSummaryView.class.getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("active");
        assertThat(((CustomerView) detail.stream()
                .filter(CustomerView.class::isInstance)
                .findFirst().orElseThrow()).active()).isNotNull();
    }
}
