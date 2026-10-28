package com.abluepoint.summer.sample.identity.api;

import org.springframework.web.bind.annotation.*;

public record AuthUserActivationRequest(boolean active) {}
