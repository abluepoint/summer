package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
class CustomerService {
    private final CustomerRepository repository;
    private final CustomerQuery customerQuery;
    private final CustomerAuditService audit;

    CustomerService(CustomerRepository repository, CustomerQuery customerQuery) {
        this(repository, customerQuery, null);
    }

    @Autowired
    CustomerService(CustomerRepository repository, CustomerQuery customerQuery, CustomerAuditService audit) {
        this.repository = repository;
        this.customerQuery = customerQuery;
        this.audit = audit;
    }

    @Transactional(readOnly=true) CustomerView findByUsername(String username) { return toView(repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND))); }
    @Transactional(readOnly=true) CustomerPage list(CustomerListQuery query) { long total=customerQuery.count(query); return new CustomerPage(customerQuery.list(query),query.page(),query.size(),total,total==0?0:((total-1)/query.size())+1,query.projection().name().toLowerCase(java.util.Locale.ROOT)); }

    @Transactional
    CustomerView create(CreateCustomerCommand c) {
        return create(c, null, null);
    }

    @Transactional
    CustomerView create(CreateCustomerCommand c, String requestId, Authentication authentication) {
        String username = c == null ? "unknown" : c.username();
        try {
            if (repository.existsByUsername(c.username())) throw conflict("username");
            if (repository.existsByEmail(c.email())) throw conflict("email");
            CustomerView result = toView(repository.saveAndFlush(new Customer(c.username(),c.email(),c.displayName())));
            recordSuccess(username, "CUSTOMER_CREATED", requestId, authentication);
            return result;
        } catch (DataIntegrityViolationException e) {
            RuntimeException mapped = uniqueConflict(e);
            recordFailure(username, "CUSTOMER_CREATED", requestId, authentication, "constraint_conflict", mapped);
            throw mapped;
        } catch (RuntimeException e) {
            recordFailure(username, "CUSTOMER_CREATED", requestId, authentication, "operation_failed", e);
            throw e;
        }
    }

    @Transactional
    CustomerView update(String username, UpdateCustomerCommand c) {
        return update(username, c, null, null);
    }

    @Transactional
    CustomerView update(String username, UpdateCustomerCommand c, String requestId, Authentication authentication) {
        String action = "CUSTOMER_UPDATED";
        try {
            Customer x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
            boolean previousActive = x.isActive();
            if (c.emailPresent() && repository.existsByEmailAndUsernameNot(c.email(), username)) throw conflict("email");
            if(c.emailPresent()) x.changeEmail(c.email());
            if(c.displayNamePresent()) x.changeDisplayName(c.displayName());
            if(c.activePresent()) {
                x.changeActive(c.active());
                if (previousActive != c.active()) action = c.active() ? "CUSTOMER_REACTIVATED" : "CUSTOMER_DEACTIVATED";
            }
            try {
                CustomerView result = toView(repository.saveAndFlush(x));
                recordSuccess(username, action, requestId, authentication);
                return result;
            } catch (DataIntegrityViolationException e) {
                throw uniqueConflict(e);
            }
        } catch (RuntimeException e) {
            recordFailure(username, action, requestId, authentication, "operation_failed", e);
            throw e;
        }
    }

    @Transactional
    void delete(String username) {
        delete(username, null, null);
    }

    @Transactional
    void delete(String username, String requestId, Authentication authentication) {
        try {
            Customer x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
            recordSuccess(username, "CUSTOMER_DELETED", requestId, authentication);
            repository.delete(x);
        } catch (RuntimeException e) {
            recordFailure(username, "CUSTOMER_DELETED", requestId, authentication, "operation_failed", e);
            throw e;
        }
    }

    private void recordSuccess(String username, String action, String requestId, Authentication authentication) {
        if (audit != null) audit.recordSuccess(username, action, requestId, authentication);
    }

    private void recordFailure(String username, String action, String requestId, Authentication authentication,
                               String reason, RuntimeException original) {
        if (audit == null) return;
        try {
            audit.recordFailure(username, action, requestId, authentication, reason);
        } catch (RuntimeException auditFailure) {
            original.addSuppressed(auditFailure);
        }
    }

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
