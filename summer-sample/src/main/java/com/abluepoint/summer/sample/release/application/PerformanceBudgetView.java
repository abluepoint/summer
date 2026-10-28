package com.abluepoint.summer.sample.release.application;
import com.abluepoint.summer.sample.release.domain.PerformanceBudgets;

import java.util.List;

public record PerformanceBudgetView(String mode, PerformanceBudgets budgets, boolean excludesExternalToolExecution) {}
