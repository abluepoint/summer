package com.abluepoint.summer.sample.architecture;

import com.abluepoint.summer.common.error.SummerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CompiledExceptionPolicyTest {
    @TempDir Path temporary;

    @Test
    void fullCompiledHierarchyRejectsHiddenAndAnonymousDefinitionsWithoutInitializingTypes() throws Exception {
        String source;
        try (var input = getClass().getResourceAsStream("/architecture/exception-policy-fixture.java.txt")) {
            assertThat(input).isNotNull();
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path output = compile("hierarchy", source);
        System.clearProperty("summer.guard.fixture.initialized");
        try (var loader = loader(output)) {
            var result = CompiledExceptionPolicy.inspect(List.of(output), loader);
            assertThat(result.violations()).containsExactlyInAnyOrder("compiledfixture.Oddity", "compiledfixture.Indirect",
                    "compiledfixture.Container$Hidden", "compiledfixture.AnonymousOwner$1", "compiledfixture.Direct",
                    "compiledfixture.LooksLikeValidation", "compiledfixture.ForeignDerived", "compiledfixture.SummerDerived",
                    "compiledfixture.InitializationProbe");
            assertThat(result.ownedTypes()).contains("compiledfixture.NativeUses", "compiledfixture.InitializationProbe");
            assertThat(System.getProperty("summer.guard.fixture.initialized")).isNull();
        }
    }

    @Test
    void unavailableOwnedSuperclassFailsClosed() throws Exception {
        Path output = compile("missing", "package unavailable; class Parent {} class Child extends Parent {}");
        Files.delete(output.resolve("unavailable/Parent.class"));
        try (var loader = loader(output)) {
            assertThatThrownBy(() -> CompiledExceptionPolicy.inspect(List.of(output), loader))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot load owned type: unavailable.Child")
                    .hasCauseInstanceOf(NoClassDefFoundError.class);
        }
    }

    @Test
    void parentClasspathCannotMasqueradeAsTheSuppliedOwnedOutput() throws Exception {
        Path output = Files.createDirectories(temporary.resolve("shadow"));
        String name = SummerException.class.getName().replace('.', '/') + ".class";
        Path target = output.resolve(name);
        Files.createDirectories(target.getParent());
        try (var input = SummerException.class.getResourceAsStream("/" + name)) {
            assertThat(input).isNotNull();
            Files.copy(input, target);
        }
        assertThatThrownBy(() -> CompiledExceptionPolicy.inspect(List.of(output), getClass().getClassLoader()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside declared output");
    }

    @Test
    void duplicateBinaryNamesAcrossOwnedOutputsFailClosed() throws Exception {
        Path first = compile("first", "package duplicated; class Ordinary {}");
        Path second = compile("second", "package duplicated; class Ordinary {}");
        try (var loader = new URLClassLoader(new java.net.URL[]{first.toUri().toURL(), second.toUri().toURL()}, getClass().getClassLoader())) {
            assertThatThrownBy(() -> CompiledExceptionPolicy.inspect(List.of(first, second), loader))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Duplicate owned type: duplicated.Ordinary");
        }
    }

    @Test
    void emptyOrMissingOutputsCannotProduceAVacuousPass() throws Exception {
        assertThatThrownBy(() -> CompiledExceptionPolicy.inspect(List.of(), getClass().getClassLoader()))
                .isInstanceOf(IllegalArgumentException.class);
        Path empty = Files.createDirectories(temporary.resolve("empty"));
        assertThatThrownBy(() -> CompiledExceptionPolicy.inspect(List.of(empty), getClass().getClassLoader()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No owned classes");
        assertThatThrownBy(() -> CompiledExceptionPolicy.inspect(List.of(temporary.resolve("absent")), getClass().getClassLoader()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Path compile(String label, String source) throws Exception {
        Path directory = Files.createDirectories(temporary.resolve(label));
        Path input = directory.resolve("Fixture.java");
        Files.writeString(input, source);
        Path output = Files.createDirectories(directory.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("Architecture fixtures require the JDK compiler").isNotNull();
        String dependency = Path.of(AccessDeniedException.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator
                + Path.of(SummerException.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var diagnostics = new ByteArrayOutputStream();
        int exit = compiler.run(null, diagnostics, diagnostics, "-classpath", dependency, "-d", output.toString(), input.toString());
        assertThat(exit).as(diagnostics.toString(StandardCharsets.UTF_8)).isZero();
        return output;
    }
    private URLClassLoader loader(Path output) throws Exception {
        return new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, getClass().getClassLoader());
    }
}
