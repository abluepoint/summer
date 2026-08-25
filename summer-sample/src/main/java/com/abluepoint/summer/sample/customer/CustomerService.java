package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.shared.CustomerView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerService {

    private final CustomerRepository repository;
    private final CustomerQuery customerQuery;

    CustomerService(CustomerRepository repository, CustomerQuery customerQuery) {
        this.repository = repository;
        this.customerQuery = customerQuery;
    }

    @Transactional(readOnly = true)
    CustomerView findByEmail(String email) {
        Customer customer = repository.findByEmail(email)
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        return toView(customer);
    }

    @Transactional(readOnly = true)
    CustomerPage list(CustomerListQuery query) {
        long totalElements = customerQuery.count(query);
        long totalPages = totalElements == 0
                ? 0
                : ((totalElements - 1) / query.size()) + 1;
        return new CustomerPage(
                customerQuery.list(query),
                query.page(),
                query.size(),
                totalElements,
                totalPages,
                query.projection().name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    @Transactional
    CustomerView update(String email, UpdateCustomerCommand command) {
        Customer customer = repository.findByEmail(email)
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        if (command.displayNamePresent()) {
            customer.changeDisplayName(command.displayName());
        }
        if (command.activePresent()) {
            customer.changeActive(command.active());
        }
        return toView(customer);
    }

    @Transactional
    void delete(String email) {
        Customer customer = repository.findByEmail(email)
                .orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        repository.delete(customer);
    }

    @Transactional
    CustomerView create(CreateCustomerCommand command) {
        if (repository.findByEmail(command.email()).isPresent()) {
            throw new SummerException(CommonErrorCode.CONFLICT);
        }
        Customer saved = repository.save(new Customer(command.email(), command.displayName()));
        return toView(saved);
    }

    private CustomerView toView(Customer customer) {
        return new CustomerView(
                customer.getId(),
                customer.getEmail(),
                customer.getDisplayName(),
                customer.isActive()
        );
    }
}
