package com.abluepoint.summer.sample.identity;

import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface SessionSnapshotService {

    SessionSnapshot snapshot(Authentication authentication);
}
