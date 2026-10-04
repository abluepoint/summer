package com.abluepoint.summer.sample.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CustomerAuditEventRepository extends JpaRepository<CustomerAuditEventEntity, String>,
        JpaSpecificationExecutor<CustomerAuditEventEntity> {
}
