package com.abluepoint.summer.sample.shared;

public record CustomerView(Long customerId, String email, String displayName, Boolean active) {
}
