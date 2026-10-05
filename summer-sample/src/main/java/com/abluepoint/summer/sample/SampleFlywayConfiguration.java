package com.abluepoint.summer.sample;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Runs the Sample's explicit database migrations before application runners seed data. */
@Configuration(proxyBeanMethods = false)
class SampleFlywayConfiguration {

    @Bean
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    Flyway flyway(
            DataSource dataSource,
            @Value("${spring.flyway.locations:classpath:db/migration/mysql}") String locations
    ) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations.split(","))
                .validateMigrationNaming(true)
                .load();
    }

    /**
     * Local equivalent of Boot's migration initializer. Keeping this bean unconditional
     * makes disabling Flyway a valid configuration while still providing a stable
     * dependency target for services that read migrated tables during construction.
     */
    @Bean(name = "flywayMigrationInitializer")
    FlywayMigrationInitializer flywayMigrationInitializer(ObjectProvider<Flyway> flyway) {
        Flyway configured = flyway.getIfAvailable();
        if (configured != null) configured.migrate();
        return new FlywayMigrationInitializer();
    }

    static final class FlywayMigrationInitializer {
    }
}
