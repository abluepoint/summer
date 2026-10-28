package com.abluepoint.summer.sample.key.domain;

import java.util.Optional;
public interface KeyRotationStateStore {
    Optional<KeyRotationState> current();
    Optional<KeyRotationState> lockForRotation();
    void saveAndFlush(KeyRotationState state);
}
