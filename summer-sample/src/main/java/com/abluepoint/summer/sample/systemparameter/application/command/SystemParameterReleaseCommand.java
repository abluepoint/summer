package com.abluepoint.summer.sample.systemparameter.application.command;
import com.abluepoint.summer.sample.systemparameter.domain.*;
public record SystemParameterReleaseCommand(Long revision, Long expectedVersion, String idempotencyKey) {}
