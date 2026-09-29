package com.abluepoint.summer.sample.customer;
import com.abluepoint.summer.data.jpa.repository.BaseRepository;
import java.util.Optional;
interface CustomerRepository extends BaseRepository<Customer, Long> {
    Optional<Customer> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
