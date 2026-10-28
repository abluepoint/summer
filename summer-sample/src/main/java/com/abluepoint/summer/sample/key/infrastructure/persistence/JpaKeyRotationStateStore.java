package com.abluepoint.summer.sample.key.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import java.util.Optional;
import com.abluepoint.summer.sample.key.domain.KeyRotationState;
import com.abluepoint.summer.sample.key.domain.KeyRotationStateStore;

@Repository
public class JpaKeyRotationStateStore implements KeyRotationStateStore {
    private final KeyRotationStateRepository states;
    public JpaKeyRotationStateStore(KeyRotationStateRepository states) { this.states = states; }
    @Override public Optional<KeyRotationState> current() { return states.findById(1).map(KeyRotationStateEntity::toDomain); }
    @Override public Optional<KeyRotationState> lockForRotation() { return states.lockForRotation().map(KeyRotationStateEntity::toDomain); }
    @Override public void saveAndFlush(KeyRotationState state) {
        var entity = states.findById(1).orElseThrow(() -> new IllegalStateException("SUMMER_KEY_ROTATION_STATE_MISSING"));
        entity.apply(state);
        states.saveAndFlush(entity);
    }
}
