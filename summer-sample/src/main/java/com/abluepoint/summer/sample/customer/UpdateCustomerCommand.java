package com.abluepoint.summer.sample.customer;

record UpdateCustomerCommand(
        boolean displayNamePresent,
        String displayName,
        boolean activePresent,
        boolean active
) {
}
