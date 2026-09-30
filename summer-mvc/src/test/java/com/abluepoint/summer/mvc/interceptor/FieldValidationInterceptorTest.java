package com.abluepoint.summer.mvc.interceptor;

import com.abluepoint.summer.mvc.annotation.Field;
import com.abluepoint.summer.mvc.annotation.FieldList;
import com.abluepoint.summer.mvc.annotation.FieldSource;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import com.abluepoint.summer.mvc.validate.FieldValues;
import com.abluepoint.summer.mvc.validate.PatternStyle;
import com.abluepoint.summer.mvc.validate.StyleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldValidationInterceptorTest {

    @Test
    void requiresAPositiveGlobalFieldListLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FieldValidationInterceptor(
                new StyleRegistry(Map.of()),
                0
        )).withMessage("maxListSize must be positive");
    }

    @Test
    void validatesAndExportsTypedFieldValues() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("age", "42");

        boolean proceed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("create"))
        );

        assertThat(proceed).isTrue();
        assertThat(FieldValues.from(request).get("age", Integer.class)).isEqualTo(42);
    }

    @Test
    void aggregatesRequiredAndStyleViolationsWithoutExportingPartialValues() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "non-blank text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("email", "   ");
        request.addParameter("age", "not-a-number");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("invalid"))
        )).isInstanceOfSatisfying(FieldValidationException.class, exception ->
                assertThat(exception.violations()).containsExactly(
                        new FieldValidationException.Violation("email", "must be non-blank text"),
                        new FieldValidationException.Violation("age", "must be an integer")
                ));
        assertThat(FieldValues.from(request).asMap()).isEmpty();
    }

    @Test
    void validatesAndExportsTypedFieldLists() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "non-blank text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("items[0].sku", "A-1");
        request.addParameter("items[0].quantity", "2");
        request.addParameter("items[1].sku", "B-2");
        request.addParameter("items[1].quantity", "5");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("batch"))
        );

        assertThat(FieldValues.from(request).asMap().get("items")).isEqualTo(List.of(
                Map.of("sku", "A-1", "quantity", 2),
                Map.of("sku", "B-2", "quantity", 5)
        ));
    }

    @Test
    void ordersParameterFieldListValuesByIndexInsteadOfParameterOrder() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("items[1].quantity", "5");
        request.addParameter("items[1].sku", "B-2");
        request.addParameter("items[0].quantity", "2");
        request.addParameter("items[0].sku", "A-1");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("batch"))
        );

        assertThat(FieldValues.from(request).asMap().get("items")).isEqualTo(List.of(
                Map.of("sku", "A-1", "quantity", 2),
                Map.of("sku", "B-2", "quantity", 5)
        ));
    }

    @Test
    void rejectsLegacyCollectionCountParameters() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("items.count", "1");
        request.addParameter("items[0].sku", "A-1");
        request.addParameter("items[0].quantity", "2");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("batch"))
        )).isInstanceOfSatisfying(FieldValidationException.class, exception ->
                assertThat(exception.violations()).contains(
                        new FieldValidationException.Violation("items.count", "is not allowed")
                ));
    }

    @Test
    void rejectsFieldListsFromPathVariables() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "non-blank text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of(
                "items.count", "1",
                "items[0].sku", "A-1",
                "items[0].quantity", "2"
        ));

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("pathBatch"))
        )).isInstanceOf(FieldValidationException.class);
    }

    @Test
    void rejectsNonContiguousQueryFieldListIndexes() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle", new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle", new PatternStyle(String.class, "text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("items[0].sku", "A");
        request.addParameter("items[0].quantity", "1");
        request.addParameter("items[2].sku", "C");
        request.addParameter("items[2].quantity", "3");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("batch"))
        )).isInstanceOfSatisfying(FieldValidationException.class, exception ->
                assertThat(exception.violations()).contains(
                        new FieldValidationException.Violation(
                                "items", "indexes must start at 0 and be contiguous"
                        )
                ));
    }

    @Test
    void reportsMissingFieldListAsAFieldViolation() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "non-blank text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("batch"))
        )).isInstanceOfSatisfying(FieldValidationException.class, exception ->
                assertThat(exception.violations()).containsExactly(
                        new FieldValidationException.Violation("items", "is required")
                ));
    }

    @Test
    void rejectsFieldListsAboveTheAnnotationLimitBeforeIteratingItems() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+")),
                        "textStyle",
                        new PatternStyle(String.class, "non-blank text", Map.of(), Pattern.compile(".*\\S.*"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("items[0].sku", "A");
        request.addParameter("items[0].quantity", "1");
        request.addParameter("items[1].sku", "B");
        request.addParameter("items[1].quantity", "1");
        request.addParameter("items[2].sku", "C");
        request.addParameter("items[2].quantity", "1");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("batch"))
        )).isInstanceOfSatisfying(FieldValidationException.class, exception ->
                assertThat(exception.violations()).containsExactly(
                        new FieldValidationException.Violation("items", "must contain at most 2 items")
                ));
    }

    @Test
    void rejectsFieldListsWithoutItemDefinitions() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of()),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("emptyItems.count", "0");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("emptyItems"))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("FieldList items must not be empty: emptyItems");
    }

    @Test
    void reportsStyleConversionFailuresAsFieldViolations() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("age", "999999999999999999999");

        assertThatThrownBy(() -> interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("create"))
        )).isInstanceOfSatisfying(FieldValidationException.class, exception ->
                assertThat(exception.violations()).containsExactly(
                        new FieldValidationException.Violation("age", "must be an integer")
                ));
    }

    @Test
    void supportsComposedFieldAnnotations() throws Exception {
        FieldValidationInterceptor interceptor = new FieldValidationInterceptor(
                new StyleRegistry(Map.of(
                        "integerStyle",
                        new PatternStyle(Integer.class, "an integer", Map.of(), Pattern.compile("-?\\d+"))
                )),
                100
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("age", "42");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new FieldController(), FieldController.class.getDeclaredMethod("composed"))
        );

        assertThat(FieldValues.from(request).get("age", Integer.class)).isEqualTo(42);
    }

    private static final class FieldController {

        @Field(param = "age", required = true, style = "integerStyle")
        void create() {
        }

        @Field(param = "email", required = true, style = "textStyle")
        @Field(param = "age", required = true, style = "integerStyle")
        void invalid() {
        }

        @FieldList(
                param = "items",
                maxSize = 2,
                items = {
                        @Field(param = "sku", name = "sku", required = true, style = "textStyle"),
                        @Field(param = "quantity", name = "quantity", required = true, style = "integerStyle")
                }
        )
        void batch() {
        }

        @FieldList(
                param = "items",
                items = {
                        @Field(param = "sku", name = "sku", required = true, style = "textStyle"),
                        @Field(param = "quantity", name = "quantity", required = true, style = "integerStyle")
                }
        )
        void pathBatch() {
        }

        @FieldList(param = "emptyItems")
        void emptyItems() {
        }

        @IntegerAge
        void composed() {
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Field(param = "age", required = true, style = "integerStyle")
    private @interface IntegerAge {
    }
}
