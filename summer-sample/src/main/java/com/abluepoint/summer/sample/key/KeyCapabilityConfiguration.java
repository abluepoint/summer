package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
class KeyCapabilityConfiguration {
    @Bean
    MysqlSummerKeyStore mysqlSummerKeyStore(KeyMaterialRepository repository,
                                       @Value("${SUMMER_SECURITY_MASTER_KEY:${summer.sample.security.master-key:}}") String masterKey) {
        return new MysqlSummerKeyStore(repository, masterKey);
    }
}
