package com.abluepoint.summer.sample.resource.infrastructure.configuration;

import com.abluepoint.summer.sample.identity.api.AuthUserController;
import com.abluepoint.summer.sample.key.api.KeyCapabilityController;
import com.abluepoint.summer.sample.resource.domain.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class InterfaceResourceDomainConfigurationTest {
    @ParameterizedTest
    @MethodSource("splitRequests")
    void productionCatalogRetainsOnlyTheObservedHandlerAcrossRequestSplits(Endpoint endpoint) {
        new ApplicationContextRunner().withUserConfiguration(InterfaceResourceDomainConfiguration.class).run(context -> {
            var policy = context.getBean(InterfaceResourceBindingPolicy.class);
            var method = Arrays.stream(endpoint.controller().getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(endpoint.method())).findFirst().orElseThrow();
            String handler = endpoint.controller().getName() + "#" + method.getName() + Arrays.toString(method.getParameterTypes());
            var previous = descriptor(endpoint, endpoint.previous());
            var current = descriptor(endpoint, handler);
            var protectedCatalog = policy.protect(new InterfaceResourceCatalog(List.of(current)),
                    List.of(new InterfaceResourceCatalogView(List.of(previous), 1)));
            assertThat(protectedCatalog.items().getFirst()).isEqualTo(current);
            assertThat(policy.isReplaced(current.permissionCode(), handler)).isFalse();

            String replacementHandler = handler.replace("#" + method.getName() + "[", "#replacement[");
            var replacement = descriptor(endpoint, replacementHandler);
            var rejected = policy.protect(new InterfaceResourceCatalog(List.of(replacement)),
                    List.of(new InterfaceResourceCatalogView(List.of(previous), 1)));
            assertThat(rejected.items().getFirst().configurationPolicy()).isEqualTo("REPLACED_REQUIRES_EXPLICIT_PERMISSION");
            policy.remember(rejected);
            assertThat(policy.isReplaced(replacement.permissionCode(), replacementHandler)).isTrue();
        });
    }

    private static Stream<Endpoint> splitRequests() {
        return Stream.of(
                new Endpoint(AuthUserController.class, "create", "POST", "/api/users",
                        "com.abluepoint.summer.sample.identity.AuthUserController#create[class com.abluepoint.summer.sample.identity.AuthUserController$CreateUserRequest]"),
                new Endpoint(AuthUserController.class, "active", "PATCH", "/api/users/{username}",
                        "com.abluepoint.summer.sample.identity.AuthUserController#active[class java.lang.String, class com.abluepoint.summer.sample.identity.AuthUserController$ActiveUserRequest]"),
                new Endpoint(KeyCapabilityController.class, "rotate", "POST", "/api/security/keys:rotate",
                        "com.abluepoint.summer.sample.key.KeyCapabilityController#rotate[class com.abluepoint.summer.sample.key.KeyCapabilityController$RotationRequest, class java.lang.String, class java.lang.String, interface org.springframework.security.core.Authentication]"));
    }

    private static InterfaceResourceDescriptor descriptor(Endpoint endpoint, String handler) {
        return InterfaceResourceDescriptor.mvc(endpoint.verb(), endpoint.path(), handler, "application", ResourceType.BUSINESS,
                SensitiveLevel.SENSITIVE, null, true);
    }

    private record Endpoint(Class<?> controller, String method, String verb, String path, String previous) {}
}
