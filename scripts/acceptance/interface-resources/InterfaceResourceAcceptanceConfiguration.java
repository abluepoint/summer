package acceptance;

import com.abluepoint.summer.sample.resource.ResourcePermission;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.function.*;

/** External acceptance fixture. Never package this in the delivered application. */
@Configuration(proxyBeanMethods = false)
public class InterfaceResourceAcceptanceConfiguration {
    @Bean StableController stableCatalogFixture() { return new StableController(); }
    @Bean RouterFunction<ServerResponse> functionalCatalogFixture() {
        return RouterFunctions.route().path("/api/acceptance/catalog", routes -> routes.GET("/functional", request -> ServerResponse.ok().body("fixture"))).build();
    }
    @Bean @ConditionalOnProperty(name="acceptance.catalog.phase", havingValue="original")
    OriginalController originalCatalogFixture() { return new OriginalController(); }
    @Bean @ConditionalOnProperty(name="acceptance.catalog.phase", havingValue="replacement")
    ReplacementController replacementCatalogFixture() { return new ReplacementController(); }
    @Bean @ConditionalOnProperty(name="acceptance.catalog.phase", havingValue="opaque")
    HandlerMapping opaqueCatalogFixture() { return request -> null; }
    @Bean @ConditionalOnProperty(name="acceptance.catalog.phase", havingValue="conflict")
    ConflictController conflictingCatalogFixture() { return new ConflictController(); }

}

    @RestController class StableController {
        @GetMapping("/api/acceptance/catalog/explicit") @ResourcePermission("acceptance.catalog.override")
        public String read() { return "explicit"; }
    }
    @RestController class OriginalController {
        @GetMapping("/api/acceptance/catalog/reused") public String read() { return "original"; }
        @GetMapping("/api/acceptance/catalog/added") public String added() { return "added"; }
    }
    @RestController class ReplacementController {
        @GetMapping("/api/acceptance/catalog/reused") public String read() { return "replacement"; }
    }
    @RestController class ConflictController {
        @GetMapping("/api/acceptance/catalog/conflict/{id}") public String first() { return "first"; }
        @GetMapping("/api/acceptance/catalog/conflict/{name}") public String second() { return "second"; }
    }
