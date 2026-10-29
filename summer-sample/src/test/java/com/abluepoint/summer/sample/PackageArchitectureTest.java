package com.abluepoint.summer.sample;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.JavacTask;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PackageArchitectureTest {
    private static final String SAMPLE = "com.abluepoint.summer.sample.";
    private static final Set<String> TECHNICAL = Set.of("cache", "customer", "observability", "operations", "resilience", "statistics");
    private static final Set<String> DOMAIN = Set.of("identity", "bff", "enterprise", "member", "organization", "invitation",
            "application", "agent", "authorization", "resource", "configuration", "dictionary", "systemparameter", "menu",
            "connection", "mcpauthorization", "delegation", "physicalconnection", "key", "auditledger", "task", "realtime", "plugin", "release");
    private static final Pattern OUTER_BUSINESS_LAYER = Pattern.compile("com\\.abluepoint\\.summer\\.sample\\.[^.]+\\.(api|application|infrastructure)(\\..*)?");
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.abluepoint.summer");

    @Test void allThirtyResponsibilitiesUseTheirSelectedLayers() throws Exception {
        Path sample = repository().resolve("summer-sample/src/main/java/com/abluepoint/summer/sample");
        try (var directories = Files.list(sample)) {
            assertThat(directories.filter(Files::isDirectory).map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrderElementsOf(java.util.stream.Stream.concat(TECHNICAL.stream(), DOMAIN.stream()).toList());
        }
        for (String feature : DOMAIN) {
            for (String role : List.of("api", "application", "domain")) {
                assertThat(CLASSES.stream().anyMatch(type -> type.getPackageName().equals(SAMPLE + feature + "." + role)
                        || type.getPackageName().startsWith(SAMPLE + feature + "." + role + ".")))
                        .as("%s has actual %s types", feature, role).isTrue();
            }
        }
        for (JavaClass type : CLASSES) {
            if (!type.getPackageName().startsWith(SAMPLE)) continue;
            String[] segments = type.getPackageName().substring(SAMPLE.length()).split("\\.");
            if (segments.length == 0 || (!DOMAIN.contains(segments[0]) && !TECHNICAL.contains(segments[0]))) continue;
            // package-info declares the Modulith boundary at the responsibility root.
            if (type.getSimpleName().equals("package-info")) continue;
            assertThat(segments.length).as("%s belongs to an explicit layer", type.getName()).isGreaterThanOrEqualTo(2);
            if (DOMAIN.contains(segments[0])) assertThat(segments[1]).isIn("api", "application", "domain", "infrastructure");
            else assertThat(segments[1]).isIn("controller", "service", "repository", "model", "configuration", "bootstrap", "exception");
        }
    }

    @Test void domainRulesDoNotDependOnFrameworksOrOuterBusinessLayers() {
        var violations = CLASSES.stream().filter(type -> type.getPackageName().startsWith(SAMPLE)
                        && type.getPackageName().contains(".domain"))
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String target = dependency.getTargetClass().getPackageName();
                    // Modulith's marker describes an exported contract; it adds no runtime/framework collaboration.
                    return (target.startsWith("org.springframework")
                            && !dependency.getTargetClass().getName().equals("org.springframework.modulith.NamedInterface"))
                            || target.startsWith("jakarta.persistence")
                            || target.startsWith("jakarta.servlet") || target.startsWith("org.hibernate")
                            || OUTER_BUSINESS_LAYER.matcher(target).matches();
                }).map(Object::toString).sorted().toList();
        assertThat(violations).isEmpty();
    }

    @Test void oauthManagementApiDelegatesUseCasesWithoutStorageOrProtocolAssembly() {
        var api = CLASSES.get(SAMPLE + "authorization.api.OAuthClientManagementController");
        assertThat(api.getDirectDependenciesFromSelf().stream().filter(dependency -> {
            String target = dependency.getTargetClass().getName();
            return target.startsWith(SAMPLE + "authorization.infrastructure.")
                    || target.startsWith("org.springframework.security.oauth2.server.authorization.")
                    || target.startsWith("com.abluepoint.summer.security.oauth2.spi.")
                    || target.equals("com.abluepoint.summer.security.audit.spi.SecurityAuditLogger");
        }).map(Object::toString).sorted().toList()).isEmpty();
        var useCases = CLASSES.stream().filter(type -> type.getName().equals(SAMPLE
                + "authorization.application.OAuthClientManagementService")).toList();
        assertThat(useCases).hasSize(1);
        assertThat(useCases.stream().flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> dependency.getTargetClass().getPackageName().startsWith(SAMPLE + "authorization.infrastructure")
                        || dependency.getTargetClass().getPackageName().startsWith("org.springframework.web")
                        || dependency.getTargetClass().getPackageName().startsWith("org.springframework.http"))
                .map(Object::toString).sorted().toList()).isEmpty();
    }

    @Test void sampleDoesNotConsumeFrameworkInternalImplementations() {
        var violations = CLASSES.stream().filter(type -> type.getPackageName().startsWith(SAMPLE))
                .flatMap(type -> type.getDirectDependenciesFromSelf().stream())
                .filter(dependency -> {
                    String target = dependency.getTargetClass().getPackageName();
                    return target.startsWith("com.abluepoint.summer.") && !target.startsWith(SAMPLE)
                            && List.of(target.split("\\.")).contains("internal");
                }).map(Object::toString).sorted().toList();
        assertThat(violations).isEmpty();
    }

    @Test void technicalLayersRespectTheirDependencyDirection() {
        var violations = new ArrayList<String>();
        for (JavaClass type : CLASSES) {
            if (!type.getPackageName().startsWith(SAMPLE)) continue;
            String[] origin = type.getPackageName().substring(SAMPLE.length()).split("\\.");
            if (origin.length < 2 || !TECHNICAL.contains(origin[0])) continue;
            Set<String> forbidden = switch (origin[1]) {
                case "service" -> Set.of("controller");
                case "repository" -> Set.of("controller", "service");
                case "model" -> Set.of("controller", "service", "repository");
                case "controller" -> Set.of("repository");
                default -> Set.of(); // Composition/bootstrap may assemble the actual collaborators.
            };
            type.getDirectDependenciesFromSelf().stream().filter(dependency -> {
                String target = dependency.getTargetClass().getPackageName();
                if (!target.startsWith(SAMPLE + origin[0] + ".")) return false;
                String role = target.substring((SAMPLE + origin[0] + ".").length()).split("\\.")[0];
                return forbidden.contains(role);
            }).map(Object::toString).forEach(violations::add);
        }
        assertThat(violations).isEmpty();
    }

    @Test void startersContainNoRuntimeSource() throws Exception {
        try (var directories = Files.list(repository())) {
            for (Path module : directories.filter(Files::isDirectory).filter(path -> path.getFileName().toString().endsWith("-starter")).toList()) {
                Path source = module.resolve("src/main");
                if (!Files.exists(source)) continue;
                try (var files = Files.walk(source)) {
                    assertThat(files.filter(Files::isRegularFile).filter(path -> path.toString().matches(".*\\.(java|kt|groovy|scala|class)$")).toList())
                            .as("%s is a dependency entry point", module.getFileName()).isEmpty();
                }
            }
        }
    }

    @Test void frameworkTypesHaveTheirSingleApprovedHome() throws Exception {
        var names = CLASSES.stream().map(JavaClass::getName).collect(java.util.stream.Collectors.toSet());
        var sources = productionSources().stream().filter(path -> !path.startsWith(repository().resolve("summer-sample"))).toList();
        var rows = Files.readAllLines(repository().resolve("docs/architecture/framework-type-migration.tsv"), StandardCharsets.UTF_8);
        assertThat(rows).isNotEmpty();
        for (String row : rows.subList(1, rows.size())) {
            if (row.isBlank()) continue;
            String[] cells = row.split("\\t");
            assertThat(cells).as("old/new module and fully qualified type").hasSize(4);
            Path expected = repository().resolve(cells[2] + "/src/main/java/" + cells[3].replace('.', '/') + ".java");
            String simple = cells[3].substring(cells[3].lastIndexOf('.') + 1);
            assertThat(sources.stream().filter(path -> path.getFileName().toString().equals(simple + ".java")).toList())
                    .as("%s has a single source owner", simple).containsExactly(expected);
            assertThat(names).contains(cells[3]);
            if (!cells[1].equals(cells[3])) assertThat(names).doesNotContain(cells[1]);
        }
    }

    @Test void eachProductionTopLevelTypeHasOneSameNamedFile() throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("architecture checks run on the configured JDK").isNotNull();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var paths = productionSources();
        assertThat(paths).isNotEmpty();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, manager.getJavaFileObjectsFromPaths(paths));
            var violations = new ArrayList<String>();
            for (var unit : task.parse()) {
                String file = Path.of(unit.getSourceFile().toUri()).getFileName().toString();
                var types = unit.getTypeDecls().stream().filter(ClassTree.class::isInstance).map(ClassTree.class::cast).toList();
                if (file.equals("package-info.java") || file.equals("module-info.java")) continue;
                if (types.size() != 1 || !file.equals(types.getFirst().getSimpleName() + ".java"))
                    violations.add(unit.getSourceFile().getName() + ": " + types.stream().map(type -> type.getSimpleName().toString()).toList());
            }
            assertThat(diagnostics.getDiagnostics().stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).map(Object::toString).toList()).isEmpty();
            assertThat(violations).isEmpty();
        }
    }

    private static List<Path> productionSources() throws Exception {
        var paths = new ArrayList<Path>();
        try (var modules = Files.list(repository())) {
            for (Path module : modules.filter(Files::isDirectory).filter(path -> path.getFileName().toString().startsWith("summer-")).toList()) {
                Path source = module.resolve("src/main/java");
                if (!Files.exists(source)) continue;
                try (var files = Files.walk(source)) { paths.addAll(files.filter(path -> path.toString().endsWith(".java")).toList()); }
            }
        }
        return paths;
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) current = current.getParent();
        if (current == null) throw new IllegalStateException("Repository root not found");
        return current;
    }
}
