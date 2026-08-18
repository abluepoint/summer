package com.abluepoint.summer.sample.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CustomerPersistenceTest {

    @Autowired
    CustomerRepository repository;

    @Test
    void initializesEntityIdentityAndAuditFieldsWhenPersisted() {
        Customer saved = repository.saveAndFlush(new Customer("audit@summer.local", "Audit Customer"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }
}
