package com.abluepoint.summer.sample.customer.repository;

import com.abluepoint.summer.sample.customer.model.entity.CustomerEntity;
import com.abluepoint.summer.data.jpa.repository.BaseRepository;
import java.util.Optional;
public interface CustomerRepository extends BaseRepository<CustomerEntity, Long> {
    Optional<CustomerEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByEmailAndUsernameNot(String email, String username);
}
