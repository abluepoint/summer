package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record ConnectionServiceView(String serviceId, String serviceCode, String name, String permitAudience, String protocolVersion, String status, long version, Instant lastSeenAt) {}
