package com.abluepoint.summer.common.autoconfigure;

import com.abluepoint.summer.common.message.MessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SummerCommonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SummerCommonAutoConfiguration.class);

    @Test
    void providesMessageResolverByDefaultAndSupportsDisableSwitch() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(MessageResolver.class));
        contextRunner.withPropertyValues("summer.common.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MessageResolver.class));
    }
}
