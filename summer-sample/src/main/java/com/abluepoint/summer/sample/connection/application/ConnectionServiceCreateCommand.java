package com.abluepoint.summer.sample.connection.application;


import java.time.Instant;
import java.util.List;

public record ConnectionServiceCreateCommand(String serviceCode, String name, String permitAudience, String protocolVersion) {}
