package com.abluepoint.summer.sample.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Records only the random schemas owned by an explicitly instrumented native test run. */
public final class NativeDatabaseEvidence {
    private static final java.util.Set<String> CLAIMED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private NativeDatabaseEvidence() {}

    public static String claim(Class<?> owner, String schema) {
        record(schema, "owner=" + owner.getName() + "\nstate=claimed\n");
        CLAIMED.add(schema);
        return schema;
    }

    /** Called only immediately after this fixture's CREATE DATABASE has succeeded. */
    public static void created(Class<?> owner, String schema) {
        if (!CLAIMED.contains(schema)) throw new IllegalStateException("Native schema must be claimed before creation");
        record(schema, "owner=" + owner.getName() + "\nstate=create-succeeded\n");
    }

    /** Restricts explicit current test installation to a schema claimed by this test JVM. */
    public static boolean claimed(String schema) {
        return schema != null && CLAIMED.contains(schema);
    }

    /** Called only after the fixture has dropped its schema and asserted that it is absent. */
    public static void removed(String schema) {
        record(schema, "state=removed-and-absence-verified\n");
        CLAIMED.remove(schema);
    }

    private static void record(String schema, String entry) {
        String directory = System.getProperty("summer.verification.nativeEvidenceDirectory");
        if (directory == null) return;
        if (!schema.matches("summer_[a-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid native test schema identifier");
        }
        try {
            Path root = Path.of(directory);
            Files.createDirectories(root);
            Files.writeString(root.resolve(schema + ".txt"), entry,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot record native test database ownership", failure);
        }
    }
}
