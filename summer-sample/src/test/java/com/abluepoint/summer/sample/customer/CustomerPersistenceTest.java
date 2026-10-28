package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.sample.customer.model.entity.CustomerEntity;
import com.abluepoint.summer.sample.customer.model.request.CustomerListRequest;
import com.abluepoint.summer.sample.customer.repository.CustomerRepository;
import com.abluepoint.summer.sample.customer.service.CustomerQueryService;

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
    CustomerQueryService customerQuery;

    @Test
    void initializesEntityIdentityAndAuditFieldsWhenPersisted() {
        CustomerEntity saved = repository.saveAndFlush(new CustomerEntity("audit", "audit@summer.local", "Audit Customer"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void complexReadsUseNarrowSummaryAndDetailProjections() {
        repository.saveAndFlush(new CustomerEntity("projection", "projection@summer.local", "Projection Customer"));

        List<?> summary = customerQuery.list(new CustomerListRequest(
                0, 20, null, CustomerListRequest.SortField.USERNAME,
                CustomerListRequest.SortDirection.ASC, CustomerListRequest.Projection.SUMMARY));
        List<?> detail = customerQuery.list(new CustomerListRequest(
                0, 20, null, CustomerListRequest.SortField.USERNAME,
                CustomerListRequest.SortDirection.ASC, CustomerListRequest.Projection.DETAIL));

        assertThat(summary).isNotEmpty().allMatch(row -> row.getClass().getSimpleName().equals("CustomerSummaryView"));
        assertThat(detail).isNotEmpty().allMatch(row -> row.getClass().getSimpleName().equals("CustomerView"));
        assertThat(List.of(summary.getFirst().getClass().getRecordComponents()))
                .extracting(component -> component.getName())
                .doesNotContain("active");
        assertThat(List.of(detail.getFirst().getClass().getRecordComponents()))
                .extracting(component -> component.getName())
                .contains("active");
    }
}
