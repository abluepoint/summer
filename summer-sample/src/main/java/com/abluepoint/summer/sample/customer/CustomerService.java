package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.shared.CustomerView;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class CustomerService {
    private final CustomerRepository repository; private final CustomerQuery customerQuery;
    CustomerService(CustomerRepository repository, CustomerQuery customerQuery) { this.repository=repository; this.customerQuery=customerQuery; }
    @Transactional(readOnly=true) CustomerView findByUsername(String username) { return toView(repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND))); }
    @Transactional(readOnly=true) CustomerPage list(CustomerListQuery query) { long total=customerQuery.count(query); return new CustomerPage(customerQuery.list(query),query.page(),query.size(),total,total==0?0:((total-1)/query.size())+1,query.projection().name().toLowerCase(java.util.Locale.ROOT)); }
    @Transactional CustomerView create(CreateCustomerCommand c) {
        if (repository.existsByUsername(c.username())) throw conflict("username");
        if (repository.existsByEmail(c.email())) throw conflict("email");
        try { return toView(repository.saveAndFlush(new Customer(c.username(),c.email(),c.displayName()))); }
        catch (DataIntegrityViolationException e) { throw uniqueConflict(e); }
    }
    @Transactional CustomerView update(String username, UpdateCustomerCommand c) {
        Customer x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        if (c.emailPresent() && repository.existsByEmailAndUsernameNot(c.email(), username)) throw conflict("email");
        if(c.emailPresent()) x.changeEmail(c.email()); if(c.displayNamePresent()) x.changeDisplayName(c.displayName()); if(c.activePresent()) x.changeActive(c.active());
        try { return toView(repository.saveAndFlush(x)); } catch(DataIntegrityViolationException e) { throw uniqueConflict(e); }
    }
    @Transactional void delete(String username) { Customer x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND)); repository.delete(x); }
    private CustomerView toView(Customer c) { return new CustomerView(c.getId(),c.getUsername(),c.getEmail(),c.getDisplayName(),c.isActive()); }

    private static SummerException conflict(String field) {
        return new SummerException(CommonErrorCode.CONFLICT,
                List.of(new SummerException.Violation(field, "is already in use")));
    }

    static RuntimeException uniqueConflict(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String name = violation.getConstraintName();
                if ("uk_summer_customer_username".equalsIgnoreCase(name)) return conflict("username");
                if ("uk_summer_customer_email".equalsIgnoreCase(name)) return conflict("email");
            }
        }
        return exception;
    }
}
