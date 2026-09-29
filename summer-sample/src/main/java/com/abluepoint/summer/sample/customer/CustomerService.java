package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.common.error.CommonErrorCode;
import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.sample.shared.CustomerView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerService {
    private final CustomerRepository repository; private final CustomerQuery customerQuery;
    CustomerService(CustomerRepository repository, CustomerQuery customerQuery) { this.repository=repository; this.customerQuery=customerQuery; }
    @Transactional(readOnly=true) CustomerView findByUsername(String username) { return toView(repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND))); }
    @Transactional(readOnly=true) CustomerPage list(CustomerListQuery query) { long total=customerQuery.count(query); return new CustomerPage(customerQuery.list(query),query.page(),query.size(),total,total==0?0:((total-1)/query.size())+1,query.projection().name().toLowerCase(java.util.Locale.ROOT)); }
    @Transactional CustomerView create(CreateCustomerCommand c) {
        if(repository.existsByUsername(c.username()) || repository.existsByEmail(c.email())) throw new SummerException(CommonErrorCode.CONFLICT);
        try { return toView(repository.saveAndFlush(new Customer(c.username(),c.email(),c.displayName()))); } catch(DataIntegrityViolationException e) { throw new SummerException(CommonErrorCode.CONFLICT); }
    }
    @Transactional CustomerView update(String username, UpdateCustomerCommand c) {
        Customer x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND));
        if(c.emailPresent()) x.changeEmail(c.email()); if(c.displayNamePresent()) x.changeDisplayName(c.displayName()); if(c.activePresent()) x.changeActive(c.active());
        try { return toView(repository.saveAndFlush(x)); } catch(DataIntegrityViolationException e) { throw new SummerException(CommonErrorCode.CONFLICT); }
    }
    @Transactional void delete(String username) { Customer x=repository.findByUsername(username).orElseThrow(() -> new SummerException(CommonErrorCode.NOT_FOUND)); repository.delete(x); }
    private CustomerView toView(Customer c) { return new CustomerView(c.getId(),c.getUsername(),c.getEmail(),c.getDisplayName(),c.isActive()); }
}
