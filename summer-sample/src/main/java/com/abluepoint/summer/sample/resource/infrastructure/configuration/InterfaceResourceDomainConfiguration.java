package com.abluepoint.summer.sample.resource.infrastructure.configuration;

import com.abluepoint.summer.sample.resource.domain.InterfaceResourceBindingPolicy;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class InterfaceResourceDomainConfiguration {
    @Bean InterfaceResourceBindingPolicy interfaceResourceBindingPolicy() throws java.io.IOException {
        return new InterfaceResourceBindingPolicy(
                moves("/META-INF/summer/interface-resource-type-moves.properties"),
                moves("/META-INF/summer/interface-resource-handler-moves.properties"));
    }

    private static java.util.Map<String, String> moves(String resource) throws java.io.IOException {
        Properties catalog = new Properties();
        try (var input = InterfaceResourceDomainConfiguration.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Interface resource move catalog is unavailable: " + resource);
            catalog.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        var moves = new HashMap<String, String>();
        catalog.forEach((oldType, currentType) -> {
            String previous = moves.put(String.valueOf(currentType), String.valueOf(oldType));
            if (previous != null && !previous.equals(oldType)) throw new IllegalStateException("Conflicting interface resource move");
        });
        return moves;
    }
}
