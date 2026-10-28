package com.abluepoint.summer.sample.agent.infrastructure;

import com.abluepoint.summer.sample.agent.application.*;
import java.util.*;
import java.time.Instant;

public record AgentReceipt(String fingerprint, Object result) {}
