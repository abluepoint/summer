package com.abluepoint.summer.sample.application.application;

import java.util.List;

public record ApplicationCreateCommand(String name, String description, List<String> redirectUris) {}
