package com.abluepoint.summer.sample.application.application;

import java.util.List;

public record ApplicationUpdateCommand(String name, String description) {
    // Preserve the pre-migration fingerprint label without rewriting field values.
    @Override public String toString() {
        return "ApplicationUpdateRequest[name=" + name + ", description=" + description + "]";
    }
}
