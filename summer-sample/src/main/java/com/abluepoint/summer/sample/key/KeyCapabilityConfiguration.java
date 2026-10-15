package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import com.abluepoint.summer.security.audit.SecurityAuditLogger;
import com.abluepoint.summer.mvc.request.RequestIdAccessor;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
class KeyCapabilityConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
    KeyFailureAudit keyFailureAudit(SecurityAuditLogger audit, RequestIdAccessor requestId) {
        return new KeyFailureAudit(audit, requestId);
    }

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
    JwtEncoder mysqlKeyJwtEncoder(MysqlSummerKeyStore store, KeyFailureAudit failureAudit) {
        return new AuditedKeyJwtEncoder(new NimbusJwtEncoder(store), failureAudit);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
    MysqlSummerKeyStore mysqlSummerKeyStore(KeyMaterialRepository repository,
                                            KeyRotationStateRepository rotationStates,
                                            @Value("${SUMMER_SECURITY_MASTER_KEY:${summer.sample.security.master-key:}}") String masterKey,
                                            @Value("${summer.sample.security.key-rotation.period-days:${SUMMER_SECURITY_KEY_ROTATION_PERIOD_DAYS:90}}") int periodDays,
                                            @Value("${summer.sample.security.key-rotation.retention-days:${SUMMER_SECURITY_KEY_ROTATION_RETENTION_DAYS:30}}") int retentionDays,
                                            KeyFailureAudit failureAudit) {
        return new MysqlSummerKeyStore(repository, rotationStates, masterKey, periodDays, retentionDays, failureAudit);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
    AutomaticKeyRotationService automaticKeyRotationService(MysqlSummerKeyStore store,
                                                             KeyRotationStateRepository rotationStates,
                                                             SecurityAuditLogger audit,
                                                             org.springframework.transaction.PlatformTransactionManager transactions,
                                                             com.abluepoint.summer.sample.customer.CustomerAuditService ledger) {
        return new AutomaticKeyRotationService(store, rotationStates, audit, transactions, ledger);
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "reference")
    ReferenceSummerKeyStore referenceSummerKeyStore(
            Environment environment,
            @Value("${summer.sample.security.key-reference.source:}") String source,
            @Value("${summer.sample.security.key-reference.name:${summer.security.key-reference.name:}}") String name,
            @Value("${summer.sample.security.key-reference.allowed-directory:${summer.security.key-reference.allowed-directory:}}") String allowedDirectory,
            @Value("${summer.sample.security.key-reference.kid:${summer.security.key-reference.kid:}}") String kid) {
        return referenceStore(environment, source, name, allowedDirectory, kid, "environment");
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "environment")
    ReferenceSummerKeyStore environmentSummerKeyStore(
            Environment environment,
            @Value("${summer.sample.security.key-reference.source:environment}") String source,
            @Value("${summer.sample.security.key-reference.name:${summer.security.key-reference.name:}}") String name,
            @Value("${summer.sample.security.key-reference.allowed-directory:${summer.security.key-reference.allowed-directory:}}") String allowedDirectory,
            @Value("${summer.sample.security.key-reference.kid:${summer.security.key-reference.kid:}}") String kid) {
        return referenceStore(environment, source, name, allowedDirectory, kid, "environment");
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "file")
    ReferenceSummerKeyStore fileSummerKeyStore(
            Environment environment,
            @Value("${summer.sample.security.key-reference.source:file}") String source,
            @Value("${summer.sample.security.key-reference.name:${summer.security.key-reference.name:}}") String name,
            @Value("${summer.sample.security.key-reference.allowed-directory:${summer.security.key-reference.allowed-directory:}}") String allowedDirectory,
            @Value("${summer.sample.security.key-reference.kid:${summer.security.key-reference.kid:}}") String kid) {
        return referenceStore(environment, source, name, allowedDirectory, kid, "file");
    }

    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "kubernetes-secret")
    ReferenceSummerKeyStore kubernetesSecretSummerKeyStore(
            Environment environment,
            @Value("${summer.sample.security.key-reference.source:kubernetes-secret}") String source,
            @Value("${summer.sample.security.key-reference.name:${summer.security.key-reference.name:}}") String name,
            @Value("${summer.sample.security.key-reference.allowed-directory:${summer.security.key-reference.allowed-directory:}}") String allowedDirectory,
            @Value("${summer.sample.security.key-reference.kid:${summer.security.key-reference.kid:}}") String kid) {
        return referenceStore(environment, source, name, allowedDirectory, kid, "kubernetes-secret");
    }

    private ReferenceSummerKeyStore referenceStore(Environment environment, String source, String name,
                                                   String allowedDirectory, String kid, String defaultSource) {
        return new ReferenceSummerKeyStore(environment,
                source == null || source.isBlank() ? defaultSource : source,
                name, allowedDirectory, kid);
    }
}
