package com.abluepoint.summer.sample.release.domain;
import java.util.List;
/** Snapshot of runtime prerequisites; external execution evidence remains a separate gate. */
public interface ReleaseReadinessPort { List<ReadinessCheck> snapshot(); }
