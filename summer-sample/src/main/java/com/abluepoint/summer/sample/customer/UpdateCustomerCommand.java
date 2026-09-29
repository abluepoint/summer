package com.abluepoint.summer.sample.customer;
record UpdateCustomerCommand(boolean emailPresent, String email, boolean displayNamePresent, String displayName, boolean activePresent, boolean active) {}
