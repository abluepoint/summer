package com.abluepoint.summer.sample.enterprise.infrastructure;

@org.springframework.modulith.NamedInterface("journal")
public record DirectoryReceipt(String fingerprint, String payload) {}
