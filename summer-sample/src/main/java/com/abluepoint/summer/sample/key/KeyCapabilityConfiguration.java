package com.abluepoint.summer.sample.key;

import com.abluepoint.summer.security.autoconfigure.storage.SummerKeyStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class KeyCapabilityConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "summer.security.storage", name = "keys", havingValue = "mysql")
    MysqlSummerKeyStore mysqlSummerKeyStore(KeyMaterialRepository repository,
                                       @Value("${SUMMER_SECURITY_MASTER_KEY:${summer.sample.security.master-key:}}") String masterKey) {
        return new MysqlSummerKeyStore(repository, masterKey);
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
