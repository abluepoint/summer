package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.FieldValuesArgumentResolver;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class FieldValidationMvcTest {

    @Test
    void exposesTypedFieldValuesAsAnExplicitControllerArgument() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FieldController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(new FieldValuesArgumentResolver())
                .build();

        MvcResult result = mockMvc.perform(get("/field-values").param("age", "42")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEqualTo("42");
    }

    @RestController
    private static final class FieldController {

        @GetMapping("/field-values")
        @Field(param = "age", required = true, style = "integerStyle")
        Integer fieldValues(FieldValues values) {
            return values.get("age", Integer.class);
        }
    }
}
