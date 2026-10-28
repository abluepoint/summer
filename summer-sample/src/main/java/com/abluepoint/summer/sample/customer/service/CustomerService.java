package com.abluepoint.summer.sample.customer.service;

import com.abluepoint.summer.sample.customer.model.command.CustomerCreateCommand;
import com.abluepoint.summer.sample.customer.model.command.CustomerUpdateCommand;
import com.abluepoint.summer.sample.customer.model.entity.CustomerEntity;
import com.abluepoint.summer.sample.customer.model.request.CustomerListRequest;
import com.abluepoint.summer.sample.customer.model.view.CustomerPage;
import com.abluepoint.summer.sample.customer.model.view.CustomerView;
import com.abluepoint.summer.sample.customer.repository.CustomerRepository;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.error.api.SummerException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository repository;
    private final CustomerQueryService customerQuery;
    private final CustomerAuditService audit;

    public CustomerService(CustomerRepository repository, CustomerQueryService customerQuery) {
        this(repository, customerQuery, null);
    }

    @Autowired
    public CustomerService(CustomerRepository repository, CustomerQueryService customerQuery, CustomerAuditService audit) {
        this.repository = repository;
        this.customerQuery = customerQuery;
        this.audit = audit;
    }

    @Transactional(readOnly=true) public CustomerView findByUsername(String username) { return toView(repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND))); }
    @Transactional(readOnly=true) public CustomerPage list(CustomerListRequest query) { long total=customerQuery.count(query); return new CustomerPage(customerQuery.list(query),query.page(),query.size(),total,total==0?0:((total-1)/query.size())+1,query.projection().name().toLowerCase(java.util.Locale.ROOT)); }

    @Transactional
    public CustomerView create(CustomerCreateCommand c) {
        return create(c, null, null);
    }

    @Transactional
    public CustomerView create(CustomerCreateCommand c, String requestId, Authentication authentication) {
        String username = c == null ? "unknown" : c.username();
        try {
            if (repository.existsByUsername(c.username())) throw conflict("username");
            if (repository.existsByEmail(c.email())) throw conflict("email");
            CustomerView result = toView(repository.saveAndFlush(new CustomerEntity(c.username(),c.email(),c.displayName())));
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
    public CustomerView update(String username, CustomerUpdateCommand c) {
        return update(username, c, null, null);
    }

    @Transactional
    public CustomerView update(String username, CustomerUpdateCommand c, String requestId, Authentication authentication) {
        String action = "CUSTOMER_UPDATED";
        try {
            CustomerEntity x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
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
    public void delete(String username) {
        delete(username, null, null);
    }

    @Transactional
    public void delete(String username, String requestId, Authentication authentication) {
        try {
            CustomerEntity x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
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

    private CustomerView toView(CustomerEntity c) { return new CustomerView(c.getId(),c.getUsername(),c.getEmail(),c.getDisplayName(),c.isActive()); }

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
