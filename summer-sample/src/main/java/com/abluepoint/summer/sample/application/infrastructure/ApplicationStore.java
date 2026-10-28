package com.abluepoint.summer.sample.application.infrastructure;

import com.abluepoint.summer.sample.application.application.*;
import java.util.*;
import java.time.Instant;

public final class ApplicationStore {
    public final Map<String, ApplicationEntity> apps = new LinkedHashMap<>();
    public final Map<String, ApplicationReceipt> receipts = new HashMap<>();
    public final Map<String, List<Map<String,Object>>> audit = new HashMap<>();
}
