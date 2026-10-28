package com.abluepoint.summer.sample.authorization.application.view;

import java.util.List;
import java.util.Map;

public record PolicyDetailView(
        PolicyView policy,
        List<PolicyVersionView> versions,
        List<Map<String, Object>> bindings,
        List<PolicyTestCaseView> testCases) {}
