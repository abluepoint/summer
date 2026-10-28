package com.abluepoint.summer.common.autoconfigure;

import com.abluepoint.summer.common.error.api.CommonErrorCode;
import com.abluepoint.summer.common.message.spi.MessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

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

    @Test
    void resolvesLocalizedMessagesAndFallsBackToTheErrorDefault() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("SUMMER_INVALID_ARGUMENT", Locale.FRENCH, "Valeur invalide : {0}");

        contextRunner.withBean("messageSource", StaticMessageSource.class, () -> messages)
                .run(context -> {
                    MessageResolver resolver = context.getBean(MessageResolver.class);
                    assertThat(resolver.resolve(CommonErrorCode.INVALID_ARGUMENT, new Object[]{"email"}, Locale.FRENCH))
                            .isEqualTo("Valeur invalide : email");
                    assertThat(resolver.resolve(CommonErrorCode.NOT_FOUND, new Object[0], Locale.FRENCH))
                            .isEqualTo("The requested resource was not found");
                });
    }

    @Test
    void usesTheApplicationMessageResolverInsteadOfTheDefault() {
        MessageResolver applicationResolver = (error, arguments, locale) -> "Application message";

        contextRunner.withBean(MessageResolver.class, () -> applicationResolver)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageResolver.class);
                    assertThat(context.getBean(MessageResolver.class)).isSameAs(applicationResolver);
                    assertThat(context.getBean(MessageResolver.class)
                            .resolve(CommonErrorCode.INVALID_ARGUMENT, new Object[0], Locale.ENGLISH))
                            .isEqualTo("Application message");
                });
    }
}
