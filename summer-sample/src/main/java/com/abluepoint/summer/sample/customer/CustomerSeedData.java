package com.abluepoint.summer.sample.customer;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
class CustomerSeedData implements ApplicationRunner {
    private final CustomerRepository repository;
    CustomerSeedData(CustomerRepository repository) { this.repository = repository; }
    @Override @Transactional public void run(ApplicationArguments args) {
        repository.findByUsername("demo").orElseGet(() -> repository.save(new Customer("demo", "demo@summer.local", "Summer Demo")));
    }
}
