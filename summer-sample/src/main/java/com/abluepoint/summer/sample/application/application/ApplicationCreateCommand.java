package com.abluepoint.summer.sample.application.application;

import java.util.List;

public record ApplicationCreateCommand(String name, String description, List<String> redirectUris) {
    // Preserve the pre-migration fingerprint label without rewriting field values.
    @Override public String toString() {
        return "ApplicationCreateRequest[name=" + name + ", description=" + description + ", redirectUris=" + redirectUris + "]";
    }
}
