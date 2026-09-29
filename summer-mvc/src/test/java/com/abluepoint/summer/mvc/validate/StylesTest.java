package com.abluepoint.summer.mvc.validate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StylesTest {

    @Test
    void buildsReusableStringStyleWithChainedRules() {
        Style customerEmail = Styles.string()
                .email()
                .maxLength(200)
                .build();

        assertThat(customerEmail.getParamType()).isEqualTo(String.class);
        assertThat(customerEmail.validate("user@example.com")).isTrue();
        assertThat(customerEmail.validate("not-an-email")).isFalse();
        assertThat(customerEmail.validate("a@b.co" + "x".repeat(200))).isFalse();
        assertThat(customerEmail.getTypedObject("user@example.com")).isEqualTo("user@example.com");
    }

    @Test
    void buildsTypedStylesAndComposesRules() {
        Style integer = Styles.integer().range(1, 10).build();
        Style decimal = Styles.decimal().min(new BigDecimal("1.5")).build();
        Style bool = Styles.booleanValue().build();

        assertThat(integer.validate("4")).isTrue();
        assertThat(integer.getTypedObject("4")).isEqualTo(4);
        assertThat(integer.validate("0")).isFalse();
        assertThat(decimal.validate("1.50")).isTrue();
        assertThat(decimal.getTypedObject("1.50")).isEqualTo(new BigDecimal("1.50"));
        assertThat(bool.validate("TRUE")).isTrue();
        assertThat(bool.getTypedObject("false")).isEqualTo(false);
    }

    @Test
    void rejectsInvalidBuilderArguments() {
        assertThatThrownBy(() -> Styles.string().maxLength(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Styles.integer().range(2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Styles.string().oneOf())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
