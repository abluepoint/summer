package com.abluepoint.summer.sample.identity.application.view;

import java.util.List;

public record SessionIndexPageView(String availability, List<SessionIndexView> items) {
    public SessionIndexPageView { items = List.copyOf(items); }
}
