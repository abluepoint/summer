package com.abluepoint.summer.sample.release.domain;

import java.util.List;
public record ReleaseGate(String name, boolean executed, boolean passed, String evidence) {}
