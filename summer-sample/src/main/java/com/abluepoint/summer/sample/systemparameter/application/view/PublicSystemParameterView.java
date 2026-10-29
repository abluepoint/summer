package com.abluepoint.summer.sample.systemparameter.application.view;

import org.springframework.web.bind.annotation.*;

public record PublicSystemParameterView(String key, Object value, long version) {}
