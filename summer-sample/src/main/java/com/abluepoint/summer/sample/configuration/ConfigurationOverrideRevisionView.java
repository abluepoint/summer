package com.abluepoint.summer.sample.configuration;
import java.time.Instant;
public record ConfigurationOverrideRevisionView(long revision,String state,Object value,boolean publicReadable,
        boolean enabled,String principal,Instant createdAt) {}
