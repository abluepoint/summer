package com.abluepoint.summer.sample.authorization.infrastructure.data;

public interface ApplicationRecordRepository
        extends org.springframework.data.jpa.repository.JpaRepository<
                        ApplicationRecordEntity, String>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<
                        ApplicationRecordEntity> {}
