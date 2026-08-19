package com.abluepoint.summer.sample.customer;

import com.abluepoint.summer.sample.shared.CustomerView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final CustomerService service;

    CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/{email}")
    @PreAuthorize("hasAuthority('SCOPE_customer.read')")
    CustomerView findByEmail(@PathVariable String email) {
        return service.findByEmail(email);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_customer.write')")
    CustomerView create(@Valid @RequestBody CreateCustomerRequest request) {
        return service.create(request);
    }
}
