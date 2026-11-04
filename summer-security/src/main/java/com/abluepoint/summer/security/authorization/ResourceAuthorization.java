package com.abluepoint.summer.security.authorization;

import org.springframework.security.core.Authentication;
import java.util.Objects;

/** The transport-independent business authorization boundary. Engine selection is server owned. */
@FunctionalInterface
public interface ResourceAuthorization {
    Decision decide(Request request);

    record Domain(String kind, String id) {
        public Domain {
            if (!"PLATFORM".equals(kind) && !"ENTERPRISE".equals(kind)) throw new IllegalArgumentException("Invalid domain kind");
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException("Invalid domain id");
            if ("PLATFORM".equals(kind) && !"platform".equals(id)) throw new IllegalArgumentException("Invalid platform domain");
        }
        public static Domain enterprise(String id) { return new Domain("ENTERPRISE", id); }
        public static Domain platform() { return new Domain("PLATFORM", "platform"); }
        public String key() { return kind + ":" + id; }
    }

    /** Stable resource identity; contains neither a permission code nor an access mode. */
    record Resource(String id, String type, String source) {
        public Resource {
            Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(source);
            if (id.isBlank() || type.isBlank() || source.isBlank()) throw new IllegalArgumentException("Incomplete resource identity");
        }
    }

    record Request(Domain domain, Authentication principal, Resource resource, String requestId) {
        public Request { Objects.requireNonNull(domain); Objects.requireNonNull(resource); }
    }

    record Decision(boolean allowed, String reason, long revision, Domain domain, Resource resource) {
        public Decision {
            Objects.requireNonNull(reason); Objects.requireNonNull(domain); Objects.requireNonNull(resource);
            if (revision < 0) throw new IllegalArgumentException("Invalid authorization revision");
        }
    }
}
