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
}
