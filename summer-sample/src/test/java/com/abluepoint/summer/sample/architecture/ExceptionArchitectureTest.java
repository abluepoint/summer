package com.abluepoint.summer.sample.architecture;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionArchitectureTest {
    @Test
    void ownedThrowableDefinitionsAreOnlySummerExceptionAndApprovedFieldValidation() throws Exception {
        String supplied = System.getProperty("summer.architecture.productionClasses");
        assertThat(supplied).as("Gradle must supply actual main class directories").isNotBlank();
        var outputs = Arrays.stream(supplied.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .map(Path::of).toList();
        var result = CompiledExceptionPolicy.inspect(outputs, getClass().getClassLoader());
        assertThat(result.ownedTypes()).contains(SummerException.class.getName(), FieldValidationException.class.getName());
        assertThat(result.violations()).as("Owned Throwable definitions outside the explicit architecture allowlist").isEmpty();
    }
}
