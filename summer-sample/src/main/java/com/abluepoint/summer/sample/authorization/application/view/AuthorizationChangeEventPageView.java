package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;

public record AuthorizationChangeEventPageView(List<AuthorizationChangeEventView> items, int totalElements) {}
