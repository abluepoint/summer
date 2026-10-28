package com.abluepoint.summer.sample.authorization.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationChangeEventReceiptRepository extends JpaRepository<AuthorizationChangeEventReceiptEntity, String> {}
