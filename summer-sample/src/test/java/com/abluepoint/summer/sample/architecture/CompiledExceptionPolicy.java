package com.abluepoint.summer.sample.architecture;

import com.abluepoint.summer.common.error.SummerException;
import com.abluepoint.summer.mvc.validate.FieldValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Inspects only Gradle-supplied compiled main outputs, never source directories. */
final class CompiledExceptionPolicy {
    private static final Set<Class<?>> ALLOWED = Set.of(SummerException.class, FieldValidationException.class);

    record Result(Set<String> ownedTypes, List<String> violations) {}

    static Result inspect(List<Path> outputs, ClassLoader loader) throws Exception {
        if (outputs.isEmpty()) throw new IllegalArgumentException("No compiled production outputs supplied");
        if (!SummerException.class.isAssignableFrom(FieldValidationException.class))
            throw new IllegalStateException("Approved field validation type must extend SummerException");
        Set<String> names = new HashSet<>();
        List<String> violations = new ArrayList<>();
        for (Path supplied : outputs) {
            if (Files.isSymbolicLink(supplied) || !Files.isDirectory(supplied))
                throw new IllegalArgumentException("Invalid compiled output: " + supplied);
            Path output = supplied.toRealPath();
            List<Path> classes;
            try (Stream<Path> entries = Files.walk(output)) {
                classes = entries.filter(path -> {
                    if (Files.isSymbolicLink(path)) throw new IllegalStateException("Linked compiled output: " + path);
                    return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class");
                }).sorted().toList();
            }
            for (Path file : classes) {
                if (file.getFileName().toString().equals("module-info.class")) continue;
                String relative = output.relativize(file).toString();
                String name = relative.substring(0, relative.length() - ".class".length()).replace(java.io.File.separatorChar, '.');
                if (!names.add(name)) throw new IllegalStateException("Duplicate owned type: " + name);
                Class<?> type;
                try {
                    type = Class.forName(name, false, loader);
                } catch (ClassNotFoundException | LinkageError failure) {
                    throw new IllegalStateException("Cannot load owned type: " + name, failure);
                }
                var origin = type.getProtectionDomain().getCodeSource();
                if (origin == null || !Path.of(origin.getLocation().toURI()).toRealPath().equals(output))
                    throw new IllegalStateException("Owned type loaded outside declared output: " + name);
                if (Throwable.class.isAssignableFrom(type) && !ALLOWED.contains(type)) violations.add(name);
            }
        }
        if (names.isEmpty()) throw new IllegalStateException("No owned classes found in production outputs");
        return new Result(Set.copyOf(names), List.copyOf(violations));
    }

    private CompiledExceptionPolicy() {}
}
