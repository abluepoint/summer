package com.abluepoint.summer.sample.release.application;

import java.util.List;
public record PerformanceBenchmarkView(String operation, int sampleCount, long p95Ms, double throughputPerSecond,
                                long budgetMs, boolean budgetPassed, int failed, String failureReason, String mode) {}
