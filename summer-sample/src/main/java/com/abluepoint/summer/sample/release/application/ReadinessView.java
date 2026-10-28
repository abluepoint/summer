package com.abluepoint.summer.sample.release.application;
import com.abluepoint.summer.sample.release.domain.ReadinessCheck;
import java.util.List;
public record ReadinessView(String status, List<ReadinessCheck> checks, boolean rollbackDryRunSupported) {}
