package com.abluepoint.summer.sample.customer.service;

import com.abluepoint.summer.sample.customer.model.request.CustomerListRequest;
import com.abluepoint.summer.sample.customer.model.view.CustomerSummaryView;
import com.abluepoint.summer.sample.customer.model.view.CustomerView;

import com.abluepoint.summer.data.jpa.query.api.JpaSqlTemplate;
import com.abluepoint.summer.sample.SampleDataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CustomerQueryService {

    private static final Logger log = LoggerFactory.getLogger(CustomerQueryService.class);

    private final JpaSqlTemplate sqlTemplate;
    private final SampleDataProperties dataProperties;

    public CustomerQueryService(JpaSqlTemplate sqlTemplate, SampleDataProperties dataProperties) {
        this.sqlTemplate = sqlTemplate;
        this.dataProperties = dataProperties;
    }

    public List<?> list(CustomerListRequest query) {
        List<?> customers = query.projection() == CustomerListRequest.Projection.SUMMARY
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

    public long count(CustomerListRequest query) {
        return sqlTemplate.count(
                dataProperties.sqlResource(),
                "countCustomers",
                templateParameters(query),
                countBindingParameters(query)
        );
    }

    private Map<String, Object> templateParameters(CustomerListRequest query) {
        return Map.of(
                "filterByActive", query.active() != null,
                "sortByUsername", query.sort() == CustomerListRequest.SortField.USERNAME,
                "sortByEmail", query.sort() == CustomerListRequest.SortField.EMAIL,
                "sortByDisplayName", query.sort() == CustomerListRequest.SortField.DISPLAY_NAME,
                "sortByActive", query.sort() == CustomerListRequest.SortField.ACTIVE,
                "descending", query.direction() == CustomerListRequest.SortDirection.DESC,
                "detailProjection", query.projection() == CustomerListRequest.Projection.DETAIL
        );
    }

    private Map<String, Object> bindingParameters(CustomerListRequest query) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        if (query.active() != null) {
            bindings.put("active", query.active());
        }
        bindings.put("limit", query.size());
        bindings.put("offset", query.offset());
        return bindings;
    }

    private Map<String, Object> countBindingParameters(CustomerListRequest query) {
        return query.active() == null
                ? Map.of()
                : Map.of("active", query.active());
    }
}
