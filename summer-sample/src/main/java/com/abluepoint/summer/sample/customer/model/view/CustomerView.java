package com.abluepoint.summer.sample.customer.model.view;

public record CustomerView(Long customerId, String username, String email, String displayName, Boolean active) {
}
