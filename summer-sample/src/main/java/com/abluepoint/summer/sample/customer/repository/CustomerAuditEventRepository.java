package com.abluepoint.summer.sample.customer.repository;

import com.abluepoint.summer.sample.customer.model.entity.CustomerAuditEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerAuditEventRepository extends JpaRepository<CustomerAuditEventEntity, String>,
        JpaSpecificationExecutor<CustomerAuditEventEntity> {
}
