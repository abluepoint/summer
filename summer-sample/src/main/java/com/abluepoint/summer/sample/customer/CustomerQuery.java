package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import com.abluepoint.summer.sample.SampleDataProperties;
import com.abluepoint.summer.sample.shared.CustomerSummaryView;
import com.abluepoint.summer.sample.shared.CustomerView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class CustomerQuery {

    private static final Logger log = LoggerFactory.getLogger(CustomerQuery.class);

    private final JpaSqlTemplate sqlTemplate;
    private final SampleDataProperties dataProperties;

    CustomerQuery(JpaSqlTemplate sqlTemplate, SampleDataProperties dataProperties) {
        this.sqlTemplate = sqlTemplate;
        this.dataProperties = dataProperties;
    }

    List<?> list(CustomerListQuery query) {
        List<?> customers = query.projection() == CustomerListQuery.Projection.SUMMARY
                ? sqlTemplate.query(
                        dataProperties.sqlResource(),
                        "listCustomers",
                        templateParameters(query),
                        bindingParameters(query),
                        CustomerSummaryView.class
                )
                : sqlTemplate.query(
                        dataProperties.sqlResource(),
                        "listCustomers",
                        templateParameters(query),
                        bindingParameters(query),
                        CustomerView.class
                );
        log.info("data_event=template_query template=listCustomers rows={}", customers.size());
        return customers;
    }

    long count(CustomerListQuery query) {
        return sqlTemplate.count(
                dataProperties.sqlResource(),
                "countCustomers",
                templateParameters(query),
                countBindingParameters(query)
        );
    }

    private Map<String, Object> templateParameters(CustomerListQuery query) {
        return Map.of(
                "filterByActive", query.active() != null,
                "sortByUsername", query.sort() == CustomerListQuery.SortField.USERNAME,
                "sortByDisplayName", query.sort() == CustomerListQuery.SortField.DISPLAY_NAME,
                "sortByActive", query.sort() == CustomerListQuery.SortField.ACTIVE,
                "descending", query.direction() == CustomerListQuery.SortDirection.DESC,
                "detailProjection", query.projection() == CustomerListQuery.Projection.DETAIL
        );
    }

    private Map<String, Object> bindingParameters(CustomerListQuery query) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        if (query.active() != null) {
            bindings.put("active", query.active());
        }
        bindings.put("limit", query.size());
        bindings.put("offset", query.offset());
        return bindings;
    }

    private Map<String, Object> countBindingParameters(CustomerListQuery query) {
        return query.active() == null
                ? Map.of()
                : Map.of("active", query.active());
    }
}
