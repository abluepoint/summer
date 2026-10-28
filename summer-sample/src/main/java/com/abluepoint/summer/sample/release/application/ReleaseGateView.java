package com.abluepoint.summer.sample.release.application;
import com.abluepoint.summer.sample.release.domain.ReleaseGate;
import java.util.List;
public record ReleaseGateView(String decision, String releaseVersion, boolean dryRun, List<ReleaseGate> gates) {}
