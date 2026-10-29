package com.abluepoint.summer.sample.systemparameter.application.view;

import com.abluepoint.summer.sample.systemparameter.domain.SystemParameterReleaseState;

public record SystemParameterReleaseView(
        SystemParameterView parameter, long revision, SystemParameterReleaseState state) {}
