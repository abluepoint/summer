package com.abluepoint.summer.sample.key.domain;

import java.util.List;
import java.util.Map;
public interface KeyManagementStore {
    int currentVersion();
    List<Map<String, Object>> metadata();
    KeyRotation rotate(int retentionDays, int expectedVersion, String idempotencyKey);
}
