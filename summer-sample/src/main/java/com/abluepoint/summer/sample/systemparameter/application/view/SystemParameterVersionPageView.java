package com.abluepoint.summer.sample.systemparameter.application.view;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseState;

import java.util.List;

public record SystemParameterVersionPageView(
        List<SystemParameterVersionView> items, long currentVersion, SystemParameterReleaseState currentState) {}
