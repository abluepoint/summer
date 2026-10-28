package com.abluepoint.summer.sample.release.api;
import java.util.List;
import com.abluepoint.summer.sample.release.application.PerformanceBenchmarkCommand;
public record PerformanceBenchmarkRequest(String operation, List<Long> latenciesMs, long elapsedMs, int successful, int failed) { public PerformanceBenchmarkCommand toCommand() { return new PerformanceBenchmarkCommand(operation,latenciesMs,elapsedMs,successful,failed); } }
