package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.data.jpa.template.JpaSqlTemplate;
import com.abluepoint.summer.sample.shared.CustomerView;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private static final String SQL_RESOURCE = "classpath:sql/customer-queries.sql";

    private final CustomerRepository repository;
    private final JpaSqlTemplate sqlTemplate;
    private final Counter queryCounter;

    CustomerService(CustomerRepository repository, JpaSqlTemplate sqlTemplate, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.sqlTemplate = sqlTemplate;
        this.queryCounter = Counter.builder("summer.data.template.queries")
                .description("Freemarker SQL template queries executed by the sample")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    CustomerView findByEmail(String email) {
        queryCounter.increment();
        List<CustomerView> customers = sqlTemplate.query(
                SQL_RESOURCE,
                "findByEmail",
                Map.of("activeOnly", true),
                Map.of("email", email),
                CustomerView.class
        );
        log.info("data_event=template_query template=findByEmail rows={}", customers.size());
        return customers.stream().findFirst()
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional
    CustomerView create(CreateCustomerRequest request) {
        if (repository.findByEmail(request.email()).isPresent()) {
            throw new SummerException(CommonErrorCode.CONFLICT);
        }
        Customer saved = repository.save(new Customer(request.email(), request.displayName()));
        return new CustomerView(saved.getId(), saved.getEmail(), saved.getDisplayName(), saved.isActive());
    }
}
