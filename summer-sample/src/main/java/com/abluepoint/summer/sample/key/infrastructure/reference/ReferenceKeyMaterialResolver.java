package com.abluepoint.summer.sample.key.infrastructure.reference;

import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Resolves a deployment reference while keeping the referenced value out of application configuration. */
public final class ReferenceKeyMaterialResolver {
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Z][A-Z0-9_]*");

    private ReferenceKeyMaterialResolver() {}

    static ResolvedKey resolve(Environment environment, String source, String name,
                               String allowedDirectory, String configuredKid) {
        Source kind = Source.parse(source);
        String reference = requireReference(name);
        String encoded = switch (kind) {
            case ENVIRONMENT -> resolveEnvironment(environment, reference);
            case FILE -> resolveFile(reference, allowedDirectory);
            case KUBERNETES_SECRET -> resolveKubernetesSecret(environment, reference, allowedDirectory);
        };
        RSAPrivateCrtKey privateKey = parsePrivateKey(encoded);
        String kid = configuredKid == null || configuredKid.isBlank()
                ? derivedKid(privateKey)
                : configuredKid.trim();
        if (kid.length() > 128 || kid.contains("\n") || kid.contains("\r")) {
            throw invalid("kid has an invalid format");
        }
        return new ResolvedKey(kind.externalName, kid, privateKey);
    }

    private static String resolveEnvironment(Environment environment, String name) {
        if (!ENVIRONMENT_NAME.matcher(name).matches()) {
            throw invalid("environment reference must use uppercase letters, digits and underscores");
        }
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw unavailable("environment reference is unavailable");
        return value;
    }

    private static String resolveKubernetesSecret(Environment environment, String name, String allowedDirectory) {
        if (ENVIRONMENT_NAME.matcher(name).matches()) {
            String injected = environment.getProperty(name);
            if (injected != null && !injected.isBlank()) return injected;
        }
        return resolveFile(name, allowedDirectory);
    }

    private static String resolveFile(String name, String allowedDirectory) {
        if (allowedDirectory == null || allowedDirectory.isBlank()) {
            throw invalid("allowed-directory is required for file references");
        }
        Path root = Path.of(allowedDirectory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw unavailable("allowed directory is unavailable");
        if (Path.of(name).isAbsolute()) throw invalid("file reference must be relative");
        Path candidate = root.resolve(name).normalize();
        if (!candidate.startsWith(root)) throw invalid("file reference escapes the allowed directory");
        try {
            Path realRoot = root.toRealPath();
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(realFile)) throw invalid("file reference is not an allowed regular file");
            return Files.readString(realFile, StandardCharsets.UTF_8);
        } catch (ReferenceKeyResolutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("file reference is unavailable");
        }
    }

    private static RSAPrivateCrtKey parsePrivateKey(String value) {
        try {
            String pem = value.trim();
            if (pem.contains("BEGIN RSA PRIVATE KEY")) throw invalid("PKCS#1 private keys are not supported");
            if (pem.contains("BEGIN PRIVATE KEY")) {
                int begin = pem.indexOf("-----BEGIN PRIVATE KEY-----") + "-----BEGIN PRIVATE KEY-----".length();
                int end = pem.indexOf("-----END PRIVATE KEY-----");
                if (end < begin) throw invalid("private key format is invalid");
                pem = pem.substring(begin, end);
            }
            byte[] encoded = Base64.getMimeDecoder().decode(pem);
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (ReferenceKeyResolutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("private key format is invalid");
        }
    }

    private static String derivedKid(RSAPrivateCrtKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            return "ref-" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 16);
        } catch (Exception exception) {
            throw new IllegalStateException("SUMMER_KEY_REFERENCE_FAILED", exception);
        }
    }

    private static String requireReference(String value) {
        if (value == null || value.isBlank() || value.contains("\n") || value.contains("\r")) {
            throw invalid("reference name is required");
        }
        return value.trim();
    }

    private static ReferenceKeyResolutionException invalid(String detail) {
        return new ReferenceKeyResolutionException("SUMMER_KEY_REFERENCE_INVALID", detail);
    }

    private static ReferenceKeyResolutionException unavailable(String detail) {
        return new ReferenceKeyResolutionException("SUMMER_KEY_REFERENCE_UNAVAILABLE", detail);
    }

    record ResolvedKey(String source, String kid, RSAPrivateCrtKey privateKey) {}

    enum Source {
        ENVIRONMENT("environment"), FILE("file"), KUBERNETES_SECRET("kubernetes-secret");

        private final String externalName;

        Source(String externalName) { this.externalName = externalName; }

        static Source parse(String value) {
            if (value == null || value.isBlank()) throw invalid("source is required");
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            if (Objects.equals(normalized, "env")) normalized = "environment";
            if (Objects.equals(normalized, "kubernetes")) normalized = "kubernetes-secret";
            for (Source source : values()) if (source.externalName.equals(normalized)) return source;
            throw invalid("source is unsupported");
        }
    }

    static final class ReferenceKeyResolutionException extends IllegalStateException {
        private final String code;

        ReferenceKeyResolutionException(String code, String detail) {
            super(code + ": " + detail);
            this.code = code;
        }

        String code() { return code; }
    }
}
