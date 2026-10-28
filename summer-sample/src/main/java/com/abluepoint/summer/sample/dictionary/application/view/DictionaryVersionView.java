package com.abluepoint.summer.sample.dictionary.application.view;

import java.time.Instant;

public record DictionaryVersionView(long version, String status, Instant createdAt, String createdBy) {}
