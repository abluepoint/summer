package com.abluepoint.summer.sample.release.application;

import java.util.List;
public record PerformanceBenchmarkCommand(String operation, List<Long> latenciesMs, long elapsedMs, int successful, int failed) {}
