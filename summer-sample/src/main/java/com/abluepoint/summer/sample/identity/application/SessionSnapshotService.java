package com.abluepoint.summer.sample.identity.application;

import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface SessionSnapshotService {

    SessionSnapshotView snapshot(Authentication authentication);
}
