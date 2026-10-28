package com.abluepoint.summer.sample.release.domain;
public record PerformanceAssessment(int sampleCount, long p95Ms, double throughputPerSecond, long budgetMs, String failureReason) {
 public boolean passed() { return failureReason == null; }
}
