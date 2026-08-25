package com.abluepoint.summer.mvc.annotation;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import static org.assertj.core.api.Assertions.assertThat;

class FieldAnnotationContractTest {

    @Test
    void repeatableContainersPreserveTheLegacyEmptyDefaults() throws Exception {
        assertThat(Fields.class.getDeclaredMethod("value").getDefaultValue())
                .isEqualTo(new Field[0]);
        assertThat(FieldLists.class.getDeclaredMethod("value").getDefaultValue())
                .isEqualTo(new FieldList[0]);
    }

    @Test
    void fieldListContainerCanParticipateInComposedAnnotations() {
        assertThat(FieldLists.class.getAnnotation(Target.class).value())
                .contains(ElementType.ANNOTATION_TYPE);
    }

    @Test
    void fieldSourcesAreExplicitAndRemainParamByDefault() throws Exception {
        assertThat(FieldSource.values()).containsExactly(
                FieldSource.PARAM,
                FieldSource.JSON,
                FieldSource.PATH
        );
        assertThat(Field.class.getDeclaredMethod("source").getDefaultValue())
                .isEqualTo(FieldSource.PARAM);
        assertThat(FieldList.class.getDeclaredMethod("source").getDefaultValue())
                .isEqualTo(FieldSource.PARAM);
    }

    @Test
    void nullableAndStrictJsonContractsRemainOptIn() throws Exception {
        assertThat(Field.class.getDeclaredMethod("nullable").getDefaultValue()).isEqualTo(false);
        assertThat(Item.class.getDeclaredMethod("nullable").getDefaultValue()).isEqualTo(false);
        assertThat(FieldContract.class.getDeclaredMethod("rejectUnknownJson").getDefaultValue())
                .isEqualTo(false);
    }
}
